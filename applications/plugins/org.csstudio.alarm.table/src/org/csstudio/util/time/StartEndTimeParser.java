/* 
 * Copyright (c) 2008 Stiftung Deutsches Elektronen-Synchrotron, 
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
 package org.csstudio.util.time;

import java.util.Calendar;

/** Extract relative date/time specifications for start and end times
 *  from strings.
 *  <p>
 *  The time strings can contain absolute date and time info according to
 *  the format <code>YYYY/MM/DD hh:mm:ss.sss</code> used by the
 *  {@link AbsoluteTimeParser}.
 *  When only certain pieces of the full date and time are provided,
 *  the omitted information is obtained from the current wallclock.
 *  <p>
 *  In addition, relative date and time specifications are allowed following
 *  the format of the {@link RelativeTimeParser}, i.e.
 *  <code>-3 years -3 Months 1 days 19Hours 45 minutes 0 seconds</code>.
 *  <p>
 *  When using relative date and time specifications, the reference date
 *  depends on the circumstance:
 *  <table>
 *  <tr><th>Start Specification </th><th>End Spec.</th><th>Start Time</th><th>End Time</th></tr>
 *  <tr><td>Absolute</td><td>Absolute</td><td>As given</td><td>as given</td></tr>
 *  <tr><td>Relative</td><td>Absolute</td><td>rel. to end</td><td>as given</td></tr>
 *  <tr><td>Absolute</td><td>Relative</td><td>as given</td><td>rel. to start</td></tr>
 *  <tr><td>Relative</td><td>Relative</td><td>rel. to end</td><td>rel. to 'now'</td></tr>
 *  </table>
 *  <p>
 *  Finally, some absolute time specification might follow a relative
 *  date/time, for example:
 *  <p>
 *  <code>-7 days 08:00</code> addresses a time 7 days relative to the reference
 *  point, but at exactly 08:00.
 *
 *  @see AbsoluteTimeParser
 *  @see RelativeTimeParser
 *  @see #parse(String, String)
 *  
 *  @author Sergei Chevtsov developed the original code for the
 *          Java Archive Viewer, from which this code heavily borrows.
 *  @author Kay Kasemir
 */
public class StartEndTimeParser
{
    private Calendar start, end;
    private RelativeTimeParserResult rel_start;
    private RelativeTimeParserResult rel_end;
    
    /** Parse the given start and end time strings,
     *  and return the calendar date and time obtained from that.
     *  <p>
     *  Note that even if the start and end specifications were relative,
     *  for example "-6hours" and "now", the result would of course be
     *  the absolute values, determined "right now", for "6 hours ago"
     *  resp. "now".
     *  @return Array with start and end date/time.
     *  @throws Exception On parse error.
     */
    public StartEndTimeParser(String start_text, String end_text)
        throws Exception
    {
        rel_start = RelativeTimeParser.parse(start_text);
        rel_end = RelativeTimeParser.parse(end_text);
        if (rel_start.isAbsolute() && rel_end.isAbsolute())
        {
            start = AbsoluteTimeParser.parse(start_text);
            end = AbsoluteTimeParser.parse(end_text);
            return;
        }
        else if (!rel_start.isAbsolute() && rel_end.isAbsolute())
        {
            end = AbsoluteTimeParser.parse(end_text);
            start = adjust(end, start_text, rel_start);
            return;
        }
        else if (rel_start.isAbsolute() && !rel_end.isAbsolute())
        {
            start = AbsoluteTimeParser.parse(start_text);
            end = adjust(start, end_text, rel_end);
            return;
        }
        // else !rel_start.isAbsolute() && !rel_end.isAbsolute()
        Calendar now = Calendar.getInstance();
        end = adjust(now, end_text, rel_end);
        start = adjust(end, start_text, rel_start);
    }

    /** Get the start time obtained from the given start and end strings.
     *  <p>
     *  In case relative times are involved, those were evalutaed at the
     *  time of parsing.
     *  @return Calendar value for the start time.
     */
    public final Calendar getStart()
    {   return start; }

    /** Get the end time obtained from the given start and end strings.
     *  <p>
     *  In case relative times are involved, those were evalutaed at the
     *  time of parsing.
     *  @return Calendar value for the end time.
     */
    public final Calendar getEnd()
    {   return end;  }

    /** @return <code>true</code> if the start time is absolute, i.e. there
     *          were no 'relative' pieces found.
     */
    public final boolean isAbsoluteStart()
    {   return rel_start.isAbsolute(); }
    
    /** @return <code>true</code> if the end time is absolute, i.e. there
     *          were no 'relative' pieces found.
     */
    public final boolean isAbsoluteEnd()
    {   return rel_end.isAbsolute(); }

    /** @see #isAbsoluteStart()
     *  @return RelativeTime component of the start time.
     */
    public final RelativeTime getRelativeStart()
    {   return rel_start.getRelativeTime();  }

    /** @see #isAbsoluteEnd()
     *  @return RelativeTime component of the end time.
     */
    public final RelativeTime getRelativeEnd()
    {   return rel_end.getRelativeTime();  }
    
    /** @return <code>true</code> if the end time is 'now',
     *          i.e. relative with zero offsets.
     */
    public final boolean isEndNow()
    {
        return !isAbsoluteEnd()  &&  getRelativeEnd().isNow();
    }

    /** Adjust the given date with the relative date/time pieces.
     *  @param date A date.
     *  @param relative_time Result of RelativeTimeParser.parse()
     *  @return The adjusted time (a new instance, not 'date' as passed in).
     * @throws Exception 
     */
    private static Calendar adjust(Calendar date, String text,
                    RelativeTimeParserResult relative_time) throws Exception
    {
        // Get copy of date, and patch that one
        Calendar result = Calendar.getInstance();
        result.setTimeInMillis(date.getTimeInMillis());
        
        relative_time.getRelativeTime().adjust(result);
        
        // In case there's more text after the end of the relative
        // date/time specification, for example because we got
        // "-2month 08:00", apply that absolute text to the result.
        if (relative_time.getOffsetOfNextChar() > 1  &&
            relative_time.getOffsetOfNextChar() < text.length())
            return AbsoluteTimeParser.parse(result,
                            text.substring(relative_time.getOffsetOfNextChar()));
        return result;
    }
}
