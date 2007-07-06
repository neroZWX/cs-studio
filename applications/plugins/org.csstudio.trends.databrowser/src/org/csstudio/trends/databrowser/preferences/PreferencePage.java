package org.csstudio.trends.databrowser.preferences;

import org.eclipse.jface.preference.*;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.IWorkbench;
import org.csstudio.trends.databrowser.Plugin;

/** Preference page for <code>Prederences</code>.
 *  <p>
 *  Mostly created by the Eclipse wizard:
 *  <p> 
 *  "This class represents a preference page that is contributed to the
 *   Preferences dialog. By subclassing <samp>FieldEditorPreferencePage</samp>,
 *   we can use the field support built into JFace that allows us to create a page
 *   that is small and knows how to save, restore and apply itself.
 *   <p>
 *   This page is used to modify preferences only. They are stored in the
 *   preference store that belongs to the main plug-in class. That way,
 *   preferences can be accessed directly via the preference store."
 *   @author Kay Kasemir
 */
public class PreferencePage extends FieldEditorPreferencePage implements
                IWorkbenchPreferencePage
{
    public PreferencePage()
    {
        super(GRID);
        setPreferenceStore(Plugin.getDefault().getPreferenceStore());
        setDescription(Messages.PageTitle);
    }

    /* (non-Javadoc)
     * @see org.eclipse.ui.IWorkbenchPreferencePage#init(org.eclipse.ui.IWorkbench)
     */
    public void init(IWorkbench workbench)
    { /* NOP */ }

    /** Creates the field editors.
     *  Each one knows how to save and restore itself.
     */
    @Override
    public void createFieldEditors()
    {
        final Composite parent = getFieldEditorParent();
        addField(new BooleanFieldEditor(Preferences.P_AUTOSCALE,
                        Messages.Label_Autoscale, parent));
        addField(new BooleanFieldEditor(Preferences.P_SHOW_REQUEST_TYPES,
                        Messages.Label_Show_Request_Types, parent));
        addField(new StringFieldEditor(Preferences.P_START_TIME_SPEC,
                                        Messages.StartTime, parent));
        addField(new StringFieldEditor(Preferences.P_END_TIME_SPEC,
                        Messages.EndTime, parent));
        addField(new URLListEditor(Preferences.P_URLS, parent));
        addField(new ArchiveListEditor(Preferences.P_ARCHIVES, parent));
    }
}