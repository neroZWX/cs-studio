/*******************************************************************************
 * Copyright (c) 2010 Oak Ridge National Laboratory.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 ******************************************************************************/
package org.csstudio.alarm.beast.server;

import java.util.logging.Level;

import org.csstudio.apputil.formula.Formula;
import org.csstudio.apputil.formula.VariableNode;
import org.epics.pvmanager.PVManager;
import org.epics.pvmanager.PVReader;
import org.epics.pvmanager.PVReaderListener;
import org.epics.pvmanager.data.VType;

import static org.epics.util.time.TimeDuration.ofSeconds;

import static org.epics.pvmanager.data.ExpressionLanguage.vType;

/** Filter that computes alarm enablement from expression.
 *  <p>
 *  Example:
 *  When configured with formula
 *  <pre>2*PV1 > PV2</pre>
 *  Filter will subscribe to PVs "PV1" and "PV2".
 *  For each value change in the input PVs, the formula is
 *  evaluated and the listener is notified of the result.
 *  <p>
 *  When subscribing to PVs, note that the filter uses the same
 *  mechanism as the alarm server, i.e. when the EPICS plug-in
 *  is configured to use 'alarm' subscriptions, the filter PVs
 *  will also only send updates when their alarm severity changes,
 *  NOT for all value changes.
 *  @author Kay Kasemir
 */
@SuppressWarnings("nls")
public class Filter
{
    /** Listener to notify when the filter computes a new value */
	final private FilterListener listener;

	/** Formula to evaluate */
	final private Formula formula;

	/** Variables used in the formula. May be [0], but never null */
	final private VariableNode[] variables;

	/** This array is linked to <code>variables</code>:
	 *  Same size, and there's a PV for each VariableNode.
	 */
	final private PVReader<VType> pvs[];

	/** Initialize
	 *  @param filter_expression Formula that might contain PV names
	 *  @throws Exception on error
	 */
	public Filter(final String filter_expression,
			final FilterListener listener) throws Exception
	{
		this.listener = listener;
		formula = new Formula(filter_expression, true);
		final VariableNode vars[] = formula.getVariables();
		if (vars == null)
			variables = new VariableNode[0];
		else
		    variables = vars;

		// TODO: Use one reader for all channels?
		pvs = (PVReader<VType>[]) new PVReader[variables.length];
	}

	/** Start control system subscriptions */
	public void start() throws Exception
	{
        for (int i=0; i<pvs.length; ++i)
        {
            final int index = i;
            final PVReaderListener pvlistener = new PVReaderListener()
            {
                @Override
                public void pvChanged()
                {
                    final PVReader<VType> pv = pvs[index];
                    final VariableNode var = variables[index];
                    final Exception error = pv.lastException();
                    if (error != null)
                    {
                        Activator.getLogger().log(Level.WARNING, "Error from PV " + pv.getName() + " (var. " + var.getName() + ")", error);
                        var.setValue(Double.NaN);
                    }
                    else
                    {
                        final double value = VTypeHelper.toDouble(pv.getValue());
                        Activator.getLogger().log(Level.FINER, "Filter {0}: {1} = {2}",
                                new Object[] { formula.getFormula(), pv.getName(), value });
                        var.setValue(value);
                    }
                    evaluate();
                }
            };
            pvs[i] = PVManager.read(vType(variables[i].getName())).listeners(pvlistener).timeout(ofSeconds(30)).maxRate(ofSeconds(0.5));
        }
	}

	/** Stop control system subscriptions */
	public void stop()
	{
		for (PVReader<VType> pv : pvs)
		    pv.close();
	}

	/** Evaluate filter formula with current input values */
	private void evaluate()
	{
		final double value = formula.eval();
		// TODO Only update on _change_, not whenever inputs send an update
		listener.filterChanged(value);
	}

	/** @return String representation for debugging */
    @Override
    public String toString()
    {
        return "Filter '" + formula.getFormula() + "'";
    }
}
