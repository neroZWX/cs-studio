/*
 * Copyright (c) 2010 Stiftung Deutsches Elektronen-Synchrotron,
 * Member of the Helmholtz Association, (DESY), HAMBURG, GERMANY.
 *
 * THIS SOFTWARE IS PROVIDED UNDER THIS LICENSE ON AN "../AS IS" BASIS.
 * WITHOUT WARRANTY OF ANY KIND, EXPRESSED OR IMPLIED, INCLUDING BUT NOT LIMITED
 * TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR PARTICULAR PURPOSE AND
 * NON-INFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE
 * FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
 * THE USE OR OTHER DEALINGS IN THE SOFTWARE. SHOULD THE SOFTWARE PROVE DEFECTIVE
 * IN ANY RESPECT, THE USER ASSUMES THE COST OF ANY NECESSARY SERVICING, REPAIR OR
 * CORRECTION. THIS DISCLAIMER OF WARRANTY CONSTITUTES AN ESSENTIAL PART OF THIS LICENSE.
 * NO USE OF ANY SOFTWARE IS AUTHORIZED HEREUNDER EXCEPT UNDER THIS DISCLAIMER.
 * DESY HAS NO OBLIGATION TO PROVIDE MAINTENANCE, SUPPORT, UPDATES, ENHANCEMENTS,
 * OR MODIFICATIONS.
 * THE FULL LICENSE SPECIFYING FOR THE SOFTWARE THE REDISTRIBUTION, MODIFICATION,
 * USAGE AND OTHER RIGHTS AND OBLIGATIONS IS INCLUDED WITH THE DISTRIBUTION OF THIS
 * PROJECT IN THE FILE LICENSE.HTML. IF THE LICENSE IS NOT INCLUDED YOU MAY FIND A COPY
 * AT HTTP://WWW.DESY.DE/LEGAL/LICENSE.HTM
 */
package org.csstudio.archive.common.service.mysqlimpl;

import java.util.Collection;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import org.apache.log4j.Logger;
import org.csstudio.archive.common.service.ArchiveServiceException;
import org.csstudio.archive.common.service.IArchiveEngineFacade;
import org.csstudio.archive.common.service.archivermgmt.ArchiverMgmtEntry;
import org.csstudio.archive.common.service.archivermgmt.ArchiverMonitorStatus;
import org.csstudio.archive.common.service.channel.ArchiveChannelId;
import org.csstudio.archive.common.service.channel.IArchiveChannel;
import org.csstudio.archive.common.service.channelgroup.ArchiveChannelGroupId;
import org.csstudio.archive.common.service.channelgroup.IArchiveChannelGroup;
import org.csstudio.archive.common.service.engine.ArchiveEngineId;
import org.csstudio.archive.common.service.engine.IArchiveEngine;
import org.csstudio.archive.common.service.mysqlimpl.channel.IArchiveChannelDao;
import org.csstudio.archive.common.service.mysqlimpl.channelstatus.ArchiveChannelStatus;
import org.csstudio.archive.common.service.mysqlimpl.dao.ArchiveDaoException;
import org.csstudio.archive.common.service.mysqlimpl.dao.ArchiveDaoManager;
import org.csstudio.archive.common.service.mysqlimpl.engine.IArchiveEngineDao;
import org.csstudio.archive.common.service.mysqlimpl.sample.IArchiveSampleDao;
import org.csstudio.archive.common.service.mysqlimpl.types.ArchiveTypeConversionSupport;
import org.csstudio.archive.common.service.sample.IArchiveSample;
import org.csstudio.domain.desy.epics.typesupport.EpicsSystemVariableSupport;
import org.csstudio.domain.desy.system.IAlarmSystemVariable;
import org.csstudio.domain.desy.time.TimeInstant;
import org.csstudio.platform.logging.CentralLogger;

import com.google.inject.Inject;


/**
 * Example archive service implementation to separate the processing and logic layer from
 * the data access layer.
 *
 * Uses DAO design pattern with DaoManager to handle several connections in a pool (later) and
 * facilite CRUD command infrastructure for proper multiple command transactions.
 *
 * @author bknerr
 * @since 01.11.2010
 */
public class MySQLArchiveEngineServiceImpl implements IArchiveEngineFacade {

    static final Logger LOG = CentralLogger.getInstance().getLogger(MySQLArchiveEngineServiceImpl.class);
    private static ArchiveDaoManager DAO_MGR = ArchiveDaoManager.INSTANCE;

    /**
     * Injected by GUICE construction.
     */
    private final IArchiveEngineDao _engineDao;
    private final IArchiveSampleDao _sampleDao;
    private final IArchiveChannelDao _channelDao;


    /**
     * Constructor.
     */
    @Inject
    public MySQLArchiveEngineServiceImpl(@Nonnull final IArchiveEngineDao engineDao,
                                         @Nonnull final IArchiveSampleDao sampleDao,
                                         @Nonnull final IArchiveChannelDao channelDao) {
        _engineDao = engineDao;
        _sampleDao = sampleDao;
        _channelDao = channelDao;

        ArchiveTypeConversionSupport.install();
        EpicsSystemVariableSupport.install();
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public <V, T extends IAlarmSystemVariable<V>>
    boolean writeSamples(@Nonnull final Collection<IArchiveSample<V, T>> samples) throws ArchiveServiceException {
        try {
            DAO_MGR.getSampleDao().createSamples(samples);
        } catch (final ArchiveDaoException e) {
            throw new ArchiveServiceException("Creation of samples failed.", e);
        }
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeChannelConnectionInfo(@Nonnull final ArchiveChannelId id,
                                           @Nonnull final boolean connected,
                                           @Nonnull final String info,
                                           @Nonnull final TimeInstant timestamp) throws ArchiveServiceException {
        try {
            DAO_MGR.getChannelStatusDao().createChannelStatus(new ArchiveChannelStatus(id, connected, info, timestamp));
        } catch (final ArchiveDaoException e) {
            throw new ArchiveServiceException("Creation of channel status entry failed.", e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeMonitorModeInformation(@Nonnull final ArchiveChannelId id,
                                            @Nonnull final ArchiverMonitorStatus status,
                                            @Nonnull final ArchiveEngineId engineId,
                                            @Nonnull final TimeInstant time,
                                            @Nonnull final String info) throws ArchiveServiceException {
        try {
            DAO_MGR.getArchiverMgmtDao().createMgmtEntry(new ArchiverMgmtEntry(id, status, engineId, time, info));
      } catch (final ArchiveDaoException e) {
          throw new ArchiveServiceException("Creation of archiver management entry failed.", e);
      }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @CheckForNull
    public IArchiveEngine findEngine(@Nonnull final String name) throws ArchiveServiceException {
        try {
            return DAO_MGR.getEngineDao().retrieveEngineByName(name);
        } catch (final ArchiveDaoException e) {
            throw new ArchiveServiceException("Engine information for " + name +
                                              " could not be retrieved.", e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Nonnull
    public Collection<IArchiveChannelGroup> getGroupsForEngine(@Nonnull final ArchiveEngineId id)
                                                               throws ArchiveServiceException {
        try {
            return DAO_MGR.getChannelGroupDao().retrieveGroupsByEngineId(id);
        } catch (final ArchiveDaoException e) {
            throw new ArchiveServiceException("Groups for engine " + id.asString() +
                                              " could not be retrieved.", e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Nonnull
    public Collection<IArchiveChannel> getChannelsByGroupId(@Nonnull final ArchiveChannelGroupId groupId)
                                                            throws ArchiveServiceException {
        try {
            return DAO_MGR.getChannelDao().retrieveChannelsByGroupId(groupId);
        } catch (final ArchiveDaoException e) {
            throw new ArchiveServiceException("Channels for group " + groupId.asString() +
                                              " could not be retrieved.", e);
        }
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public <V extends Comparable<? super V>>
    void writeChannelDisplayRangeInfo(@Nonnull final ArchiveChannelId id,
                                      @Nonnull final V displayLow,
                                      @Nonnull final V displayHigh) throws ArchiveServiceException {
        try {
            DAO_MGR.getChannelDao().updateDisplayRanges(id, displayLow, displayHigh);
        } catch (final ArchiveDaoException e) {
            throw new ArchiveServiceException("Channel info for " + id +
                                              " could not be updated.", e);
        }
    }
}
