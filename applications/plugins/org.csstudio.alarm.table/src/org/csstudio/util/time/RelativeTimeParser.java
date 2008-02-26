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

/** Extract relative date/time specifications from a string.
 *  
 *  @see #parse(String)
 *  
 *  @author Sergei Chevtsov developed the original code for the
 *          Java Archive Viewer, from which this code heavily borrows.
 *  @author Kay Kasemir
 */
@SuppressWarnings("nls")
public class RelativeTimeParser
{
    /** Constant to define 'now', i.e. the current wallclock date and time. */
    public static final String NOW = "now";
    
    /** Characters that are considered part of a number */
    static private final String NUMBER_CHARS = "+-0123456789";
    
    /** Extract a relative year, month, day, hour, minute, second from string.
     *  <p>
     *  The text has to follow the format
     *  <code>-3 years -3 Months 1 days 19Hours 45 minutes 0 seconds</code>.
     *  <p>
     *  Spaces between the number and the date/time identifier are allowed.
     *  Date/time identifiers may be appreviated, so
     *  <code>-3 years</code>, 
     *  <code>-3 year</code>, 
     *  <code>-3 ye</code>, and 
     *  <code>-3y</code> are all equivalent.
     *  <p>
     *  To distinguish for example minutes from month, the 'M' for month
     *  must be uppercase, while 'm' selects minutes.
     *  <p>
     *  In addition, the special case of "now" is recognized,
     *  resulting in a relative time where all pieces are 0.
     *  <p>
     *  Returns info about the location of the char after the
     *  last item that was recognized,
     *  as well as the relative year, month, day, hour, minute, second.
     *  In case nothing was found, the last item position will be
     *  &lt;0, and the relative date/time pieces are all 0.
     * 
     *  @param text
     *  @return Array [ next char, year, month, day, hour, minute, second ]
     */
    public static RelativeTimeParserResult parse(final String text)
        throws Exception
    {
        if (text.indexOf(NOW) >= 0)
            return new RelativeTimeParserResult(new RelativeTime(),
                                                text.length());
        int offset_of_next_char = 0;
        int ymdhms[] = new int[6];
        for (int i=0; i<RelativeTime.tokens.length; ++i)
        {
            int found[] = getValueOfToken(RelativeTime.tokens[i], text);
            if (found == null)
                ymdhms[i] = 0;
            else
            {   // Keep track of the right-most value in the string
                if (found[0] > offset_of_next_char)
                    offset_of_next_char = found[0];
                ymdhms[i] = found[1];
            }
        }
        return new RelativeTimeParserResult(new RelativeTime(ymdhms),
                                            offset_of_next_char);
    }
    
    /** In case text contains "-24 token", return the number 24.
     *  @param token The token to look for.
     *  @param text  The text to analyze.
     *  @return <code>null</code> if nothing was found, otherwise an array with
     *          the end position of the value-and-tag and its numeric value.
     *  @throws NumberFormatException In case token found but number won't parse.
     */
    private static int[] getValueOfToken(final String token, final String text)
        throws Exception
    {
        // Locate token.
        int token_pos[] = locateTokenPiece(token, text);
        // Nothing found?
        if (token_pos == null)
            return null;
        // Start of token is end of the number in "+-1234 token".
        int end = token_pos[0];
        // Skip optional space between the token and the number
        while (end > 0  &&  text.charAt(end-1) == ' ')
            -- end;
        int start = end;
        // Locate start of the number
        while (start > 0  &&
               NUMBER_CHARS.indexOf(text.charAt(start-1)) >= 0)
            -- start;
        // Parse the number.
        // For some reason Integer.parseInt doesn't like "+...",
        // but I want to allow that, so chop it off
        final String number = text.charAt(start) == '+' ? 
                         text.substring(start + 1, end)
                       : text.substring(start,     end);
        try
        {
            return new int[] { token_pos[1], Integer.parseInt(number) };
        }
        catch (Exception ex)
        {
            throw new Exception("Cannot parse number for '" + token + "'");
        }
    }        
    
    /** Locate position of token in text, also recognizing an abbreviated token.
     *  <p>
     *  Except when comparing only the first characters, the match is
     *  case-insensitive.
     *  @return Array with start and end of token in string,
     *          or <code>null</code>.
     *          End is actually the location of the char following the token.
     */
    public static int[] locateTokenPiece(final String token, final String text)
    {
        final String lc_text = text.toLowerCase();
        
        // Try the full token, case-insensitive,
        // then smaller pieces of the token,
        // until we finally try just the first character.
        for (int len=token.length(); len > 0; --len)
        {
            String token_piece = token.substring(0, len);
            String cooked_text = len > 1 ? lc_text : text;
            // Compare case-insensitive, unless we're down to
            // the first char, where we keep Sergei's convention.
            if (len > 1)
                token_piece = token_piece.toLowerCase();
            // Locate the token, which would mark the _end_ of the number
            int start = cooked_text.indexOf(token_piece);
            // Nothing found? Try smaller piece of token.
            if (start < 0)
                continue;
            // Found what looks like the token piece, but we only care if 
            // it's the start of a word. We don't want to accidentally
            // recognize the final 's' in "Months" as the start of "seconds".
            if (start > 0)
            {   // Char before 'start' must be a space or a number.
                char before = cooked_text.charAt(start-1);
                if (! (before == ' '  ||  NUMBER_CHARS.indexOf(before) >= 0))
                    continue;
            }
            // Assert that it's not the "m" of "minutes" in "2 month".
            // Locate the end of what we found...
            int end = start+1;
            while (end < cooked_text.length() &&
                   cooked_text.charAt(end) != ' ')
                ++end;
            // ... and compare the whole thing
            final String found = cooked_text.substring(start, end);
            if (! found.equals(token_piece))
                continue;
            return new int[] { start, end };
        }
        // nothing found.
        return null;
    }
}
