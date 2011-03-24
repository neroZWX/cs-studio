/*******************************************************************************
 * Copyright (c) 2010 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 ******************************************************************************/
package org.csstudio.archive.common.engine.model;

import java.util.concurrent.CopyOnWriteArrayList;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

import org.apache.log4j.Logger;
import org.csstudio.archive.common.engine.service.IServiceProvider;
import org.csstudio.archive.common.service.ArchiveServiceException;
import org.csstudio.archive.common.service.IArchiveEngineFacade;
import org.csstudio.archive.common.service.archivermgmt.ArchiverMonitorStatus;
import org.csstudio.archive.common.service.channel.ArchiveChannelId;
import org.csstudio.archive.common.service.engine.ArchiveEngineId;
import org.csstudio.archive.common.service.sample.ArchiveSample;
import org.csstudio.archive.common.service.sample.IArchiveSample;
import org.csstudio.data.values.IMetaData;
import org.csstudio.data.values.IValue;
import org.csstudio.domain.desy.epics.alarm.EpicsMetaData;
import org.csstudio.domain.desy.epics.alarm.EpicsSystemVariable;
import org.csstudio.domain.desy.epics.typesupport.EpicsIMetaDataTypeSupport;
import org.csstudio.domain.desy.epics.typesupport.EpicsIValueTypeSupport;
import org.csstudio.domain.desy.system.IAlarmSystemVariable;
import org.csstudio.domain.desy.time.TimeInstant.TimeInstantBuilder;
import org.csstudio.domain.desy.typesupport.TypeSupportException;
import org.csstudio.platform.logging.CentralLogger;
import org.csstudio.platform.service.osgi.OsgiServiceUnavailableException;
import org.csstudio.utility.pv.PV;
import org.csstudio.utility.pv.PVFactory;
import org.csstudio.utility.pv.PVListener;

/** Base for archived channels.
 *
 *  @author Kay Kasemir
 *  @author Bastian Knerr
 *  @param <V> the basic value type
 *  @param <T> the system variable for the basic value type
 */
@SuppressWarnings("nls")
public class ArchiveChannel<V, T extends IAlarmSystemVariable<V>> {
    /**
     * The PV listener to translate monitored values into system variables and samples.
     *
     * @author bknerr
     * @since Mar 23, 2011
     */
    private final class DesyArchivePVListener implements PVListener {
        private final String _name;

        /**
         * Constructor.
         */
        private DesyArchivePVListener(@Nonnull final String name) {
            _name = name;
        }

        @SuppressWarnings("unchecked")
        @Override
        public void pvValueUpdate(@Nonnull final PV pv) {
            try {
                if (!_connected) {
                    handleConnectInfo(_id, pv.isConnected(), pv.getStateInfo());
                    final IMetaData metaData = pv.getValue().getMetaData();
                    if (metaData != null) {
                        final EpicsMetaData data = EpicsIMetaDataTypeSupport.toMetaData(metaData,
                                                                                       _typeClass);
                        handleOnConnectMetadata(_id, data);
                    }
                    _connected = true;
                }
                final IValue value = pv.getValue();
                final EpicsSystemVariable<V> sv = (EpicsSystemVariable<V>) EpicsIValueTypeSupport.toSystemVariable(_name, value);
                final ArchiveSample<V, T> sample = new ArchiveSample<V, T>(_id, (T) sv, sv.getAlarm());
                handleNewSample(sample);

            } catch (final TypeSupportException e) {
                PV_LOG.error("Handling of newly received IValue failed. Could not be converted to ISystemVariable", e);
                return;
            } catch (final Throwable t) {
                PV_LOG.error("Unexpected exception in PVListener: " + t.getMessage(), t);
            }
        }

        @Override
        public void pvDisconnected(@CheckForNull final PV pv) {
            if (_connected && pv != null) {
                try {
                    handleConnectInfo(_id, pv.isConnected(), pv.getStateInfo());
                } catch (final EngineModelException e) {
                    PV_LOG.error("Writing of disconnection for channel " + _name + " info failed.", e);
                }
                _connected = false;
            }
        }
    }

    static final Logger PV_LOG = CentralLogger.getInstance().getLogger(PVListener.class);

    /** Channel name.
     *  This is the name by which the channel was created,
     *  not the PV name that might include decorations.
     */
    private final String _name;

    private final Class<V> _typeClass;

    final ArchiveChannelId _id;

    /** Control system PV */
    private final PV _pv;


    /** Buffer of received samples, periodically written */
    private final SampleBuffer<V, T, IArchiveSample<V, T>> _buffer;

    /** Group to which this channel belongs.
     *  <p>
     *  Using thread safe array so that HTTPD can access
     *  as well as main thread and PV
     */
    private final CopyOnWriteArrayList<ArchiveGroup> _groups =
                                new CopyOnWriteArrayList<ArchiveGroup>();


    /** Is this channel currently running?
     *  <p>
     *  PV sends another 'disconnected' event
     *  as the result of 'stop', but we don't
     *  want to log that, so we keep track of
     *  the 'running' state.
     */
    @GuardedBy("this")
    volatile boolean _isStarted = false;

    /** Most recent value of the PV.
     *  <p>
     *  This is the value received from the PV,
     *  is is not necessarily written to the archive.
     *  <p>
     */
    @GuardedBy("this")
    private T _mostRecentSysVar;

    /**
     * The most recent value send to the archive.
     */
    @GuardedBy("this")
    protected T _lastArchivedSample;

    /** Counter for received values (monitor updates) */
    private long _receivedSampleCount = 0;

    volatile boolean _connected = false;


    private IServiceProvider _provider = new IServiceProvider() {
        @Override
        @Nonnull
        public IArchiveEngineFacade getEngineFacade() throws OsgiServiceUnavailableException {
            throw new OsgiServiceUnavailableException("This is a stub. The service provider for this channel has not been set to a real implementation.");
        }
    };

    /** Construct an archive channel
     *  @param name Name of the channel (PV)
     *  @param enablement How channel affects its groups
     *  @param buffer_capacity Size of sample buffer
     *  @param last_archived_value Last value from storage, or <code>null</code>.
     * @throws EngineModelException
     */
    public ArchiveChannel(@Nonnull final String name,
                                  @Nonnull final ArchiveChannelId id,
                                  @Nullable final Class<V> clazz) throws EngineModelException {
        _name = name;
        _typeClass = clazz;
        _id = id;
        _buffer = new SampleBuffer<V, T, IArchiveSample<V, T>>(name);

        try {
            _pv = PVFactory.createPV(name);
        } catch (final Exception e) {
            throw new EngineModelException("Connection to pv failed for channel " + name, e);
        }
        _pv.addListener(new DesyArchivePVListener(name));
    }

    @SuppressWarnings("unchecked")
    protected <W extends Comparable<? super W>>
    void handleOnConnectMetadata(@Nonnull final ArchiveChannelId id,
                                 @Nonnull final EpicsMetaData data) throws EngineModelException {
        try {
            final IArchiveEngineFacade service = _provider.getEngineFacade();
            service.writeChannelDisplayRangeInfo(id,
                                                 (W) data.getGrData().getDisplayLow(),
                                                 (W) data.getGrData().getDisplayHigh());
        } catch (final OsgiServiceUnavailableException e) {
            throw new EngineModelException("Service unavailable on updating display range info.", e);
        } catch (final ArchiveServiceException e) {
            throw new EngineModelException("Internal service error on updating display range info.", e);
        }
    }

    protected void handleConnectInfo(@Nonnull final ArchiveChannelId id,
                                     final boolean connected,
                                     @Nonnull final String info) throws EngineModelException {
        try {
            final IArchiveEngineFacade service = _provider.getEngineFacade();
            service.writeChannelConnectionInfo(id, connected, info, TimeInstantBuilder.buildFromNow());
        } catch (final OsgiServiceUnavailableException e) {
            throw new EngineModelException("Service unavailable to handle channel connection info.", e);
        } catch (final ArchiveServiceException e) {
            throw new EngineModelException("Internal service error on handling channel connection info.", e);
        }
    }

    /** @return Name of channel */
    @Nonnull
    public String getName() {
        return _name;
    }

    /** @return Short description of sample mechanism */
    @Nonnull
    public String getMechanism() {
        return "MONITOR (on change)";
    }

    /** @return <code>true</code> if connected */
    public boolean isConnected() {
        return _pv.isConnected();
    }

    /** @return Human-readable info on internal state of PV */
    @CheckForNull
    public String getInternalState() {
        return _pv.getStateInfo();
    }

    /**
     * Start archiving this channel.
     * @param engineId
     * @param info human readable info about the start of this channel
     * @throws EngineModelException
     */
    public void start(@Nonnull final ArchiveEngineId engineId,
                      @Nonnull final String info) throws EngineModelException {
        try {
            if (_isStarted) {
                return;
            }
            synchronized (this) {
                _pv.start();
                _isStarted = true;
            }

            // persist the start of monitoring
            final IArchiveEngineFacade service = _provider.getEngineFacade();
            service.writeMonitorModeInformation(_id,
                                                ArchiverMonitorStatus.ON,
                                                engineId,
                                                TimeInstantBuilder.buildFromNow(),
                                                info);
        } catch (final OsgiServiceUnavailableException e) {
            throw new EngineModelException("Service unavailable on stopping archive engine channel.", e);
        } catch (final ArchiveServiceException e) {
            throw new EngineModelException("Internal service error on stopping archive engine channel.", e);
        } catch (final Exception e) {
            throw new EngineModelException("Something went wrong within Kasemir's PV stuff on channel/PV startup", e);
        }
    }

    /**
     * Stop archiving this channel
     * @throws EngineModelException
     */
    public void stop(@Nonnull final ArchiveEngineId engineId,
                     @Nonnull final String info) throws EngineModelException {
    	if (!_isStarted) {
            return;
        }
    	synchronized (this) {
    	    _isStarted = false;
    	}
        _pv.stop();

        try {
            // persist the start of monitoring
            final IArchiveEngineFacade service = _provider.getEngineFacade();
            service.writeMonitorModeInformation(_id,
                                                ArchiverMonitorStatus.OFF,
                                                engineId,
                                                TimeInstantBuilder.buildFromNow(),
                                                info);
        } catch (final OsgiServiceUnavailableException e) {
            throw new EngineModelException("Service unavailable on stopping archive engine channel.", e);
        } catch (final ArchiveServiceException e) {
            throw new EngineModelException("Internal service error on stopping archive engine channel.", e);
        }
    }

    /** @return Most recent value of the channel's PV */
    @Nonnull
    public synchronized String getCurrentValueAsString() {
        if (_mostRecentSysVar == null) {
            return "null"; //$NON-NLS-1$
        }
        return _mostRecentSysVar.getData().getValueData().toString();
    }

    @Nonnull
    public T getMostRecentValue() {
        return _mostRecentSysVar;
    }

    /** @return Count of received values */
    public synchronized long getReceivedValues() {
        return _receivedSampleCount;
    }

    /** @return Last value written to archive */
    @Nonnull
    public final String getLastArchivedValue() {
        synchronized (this) {
            if (_lastArchivedSample == null) {
                return "null"; //$NON-NLS-1$
            }
            return _lastArchivedSample.getData().getValueData().toString();
        }
    }

    /** @return Sample buffer */
    @Nonnull
    public final SampleBuffer<V, T, IArchiveSample<V, T>> getSampleBuffer() {
        return _buffer;
    }


    /** Reset counters */
    public void reset() {
        _buffer.statsReset();
        synchronized (this) {
            _receivedSampleCount = 0;
        }
    }


    protected boolean handleNewSample(@Nonnull final IArchiveSample<V, T> sample) {
        synchronized (this) {
            ++_receivedSampleCount;
            _mostRecentSysVar = sample.getSystemVariable();
        }
        _buffer.add(sample);
        return true;

    }

    @Override
    @Nonnull
    public String toString() {
        return "Channel " + getName() + ", " + getMechanism();
    }

    @Deprecated
    public boolean isEnabled() {
        return true;
    }

    public void setServiceProvider(@Nonnull final IServiceProvider provider) {
        _provider = provider;
    }
}
