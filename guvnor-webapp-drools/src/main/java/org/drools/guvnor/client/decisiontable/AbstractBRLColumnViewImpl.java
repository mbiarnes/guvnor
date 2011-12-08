/*
 * Copyright 2011 JBoss Inc
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.drools.guvnor.client.decisiontable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.drools.guvnor.client.asseteditor.drools.modeldriven.ui.RuleModelEditor;
import org.drools.guvnor.client.asseteditor.drools.modeldriven.ui.RuleModeller;
import org.drools.guvnor.client.asseteditor.drools.modeldriven.ui.RuleModellerConfiguration;
import org.drools.guvnor.client.asseteditor.drools.modeldriven.ui.templates.TemplateModellerWidgetFactory;
import org.drools.guvnor.client.asseteditor.drools.modeldriven.ui.events.TemplateVariablesChangedEvent;
import org.drools.guvnor.client.common.Popup;
import org.drools.guvnor.client.explorer.ClientFactory;
import org.drools.guvnor.client.messages.Constants;
import org.drools.guvnor.client.rpc.RuleAsset;
import org.drools.ide.common.client.modeldriven.SuggestionCompletionEngine;
import org.drools.ide.common.client.modeldriven.brl.RuleModel;
import org.drools.ide.common.client.modeldriven.brl.templates.InterpolationVariable;
import org.drools.ide.common.client.modeldriven.brl.templates.RuleModelVisitor;
import org.drools.ide.common.client.modeldriven.dt52.BRLColumn;
import org.drools.ide.common.client.modeldriven.dt52.GuidedDecisionTable52;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.shared.EventBus;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;

/**
 * An editor for BRL Column definitions
 */
public abstract class AbstractBRLColumnViewImpl<T> extends Popup
    implements
    RuleModelEditor,
    AbstractBRLColumnView,
    TemplateVariablesChangedEvent.Handler {

    protected static final Constants constants  = GWT.create( Constants.class );

    protected int                    MIN_WIDTH  = 500;
    protected int                    MIN_HEIGHT = 200;

    protected Presenter              presenter;

    @UiField(provided = true)
    RuleModeller                     ruleModeller;

    @UiField
    TextBox                          txtColumnHeader;

    @UiField
    CheckBox                         chkHideColumn;

    @UiField
    ScrollPanel                      brlEditorContainer;

    @UiField
    Button                           cmdApplyChanges;

    Widget                           popupContent;

    @SuppressWarnings("rawtypes")
    interface AbstractBRLColumnEditorBinder
        extends
        UiBinder<Widget, AbstractBRLColumnViewImpl> {
    }

    private static AbstractBRLColumnEditorBinder  uiBinder = GWT.create( AbstractBRLColumnEditorBinder.class );

    private final SuggestionCompletionEngine      sce;
    private final DTCellValueWidgetFactory        factory;

    protected final GuidedDecisionTable52         model;
    protected final ClientFactory                 clientFactory;
    protected final EventBus                      eventBus;
    protected final boolean                       isNew;

    private final BRLColumn<T>                    editingCol;
    private final BRLColumn<T>                    originalCol;

    protected final RuleModel                     ruleModel;
    protected Map<InterpolationVariable, Integer> variables;

    public AbstractBRLColumnViewImpl(final SuggestionCompletionEngine sce,
                                     final GuidedDecisionTable52 model,
                                     final boolean isNew,
                                     final RuleAsset asset,
                                     final BRLColumn<T> column,
                                     final ClientFactory clientFactory,
                                     final EventBus eventBus) {
        this.model = model;
        this.sce = sce;
        this.isNew = isNew;
        this.eventBus = eventBus;
        this.clientFactory = clientFactory;

        this.originalCol = column;
        this.editingCol = cloneBRLActionColumn( column );
        this.variables = editingCol.getVariables();

        //TODO {manstis} Limited Entry - Set-up factory for common widgets
        factory = new DTCellValueWidgetFactory( model,
                                                sce );

        setModal( false );

        this.ruleModel = getRuleModel( editingCol );
        this.ruleModeller = new RuleModeller( asset,
                                              this.ruleModel,
                                              getRuleModellerConfiguration(),
                                              new TemplateModellerWidgetFactory(),
                                              clientFactory,
                                              eventBus );

        this.popupContent = uiBinder.createAndBindUi( this );

        setHeight( getPopupHeight() + "px" );
        setWidth( getPopupWidth() + "px" );
        this.brlEditorContainer.setHeight( (getPopupHeight() - 120) + "px" );
        this.brlEditorContainer.setWidth( getPopupWidth() + "px" );
        this.cmdApplyChanges.setEnabled( variables.size() > 0 );

        //Hook-up events
        eventBus.addHandler( TemplateVariablesChangedEvent.TYPE,
                             this );
    }

    protected abstract boolean isHeaderUnique(String header);

    protected abstract RuleModel getRuleModel(BRLColumn<T> column);

    protected abstract RuleModellerConfiguration getRuleModellerConfiguration();

    public RuleModeller getRuleModeller() {
        return this.ruleModeller;
    }

    public void setPresenter(Presenter presenter) {
        this.presenter = presenter;
    }

    @Override
    public Widget getContent() {
        return popupContent;
    }

    /**
     * Width of pop-up, 1/4 of the client width or MIN_WIDTH
     * 
     * @return
     */
    private int getPopupWidth() {
        int w = Window.getClientWidth() / 4;
        if ( w < MIN_WIDTH ) {
            w = MIN_WIDTH;
        }
        return w;
    }

    /**
     * Height of pop-up, 1/2 of the client height or MIN_HEIGHT
     * 
     * @return
     */
    protected int getPopupHeight() {
        int h = Window.getClientHeight() / 2;
        if ( h < MIN_HEIGHT ) {
            h = MIN_HEIGHT;
        }
        return h;
    }

    @UiHandler("txtColumnHeader")
    void columnHanderChangeHandler(ChangeEvent event) {
        editingCol.setHeader( txtColumnHeader.getText() );
    }

    @UiHandler("chkHideColumn")
    void hideColumnClickHandler(ClickEvent event) {
        editingCol.setHideColumn( chkHideColumn.getValue() );
    }

    @UiHandler("cmdApplyChanges")
    void applyChangesClickHandler(ClickEvent event) {

        //Template Keys for column definitions
        for ( Map.Entry<InterpolationVariable, Integer> entry : variables.entrySet() ) {
            Integer value = entry.getValue();
            InterpolationVariable variable = entry.getKey();
            System.out.println( value + ") " + variable.getVarName() + " = " + variable.getFactType() + "." + variable.getFactField() );
        }

        //Validation
        if ( null == editingCol.getHeader() || "".equals( editingCol.getHeader() ) ) {
            Window.alert( constants.YouMustEnterAColumnHeaderValueDescription() );
            return;
        }
        if ( isNew ) {
            if ( !isHeaderUnique( editingCol.getHeader() ) ) {
                Window.alert( constants.ThatColumnNameIsAlreadyInUsePleasePickAnother() );
                return;
            }

        } else {
            if ( !originalCol.getHeader().equals( editingCol.getHeader() ) ) {
                if ( !isHeaderUnique( editingCol.getHeader() ) ) {
                    Window.alert( constants.ThatColumnNameIsAlreadyInUsePleasePickAnother() );
                    return;
                }
            }
        }

        // Pass new\modified column back for handling
        //refreshGrid.execute( editingCol );
        hide();
    }

    private BRLColumn<T> cloneBRLActionColumn(BRLColumn<T> col) {
        //TODO {manstis} Deep copy a RuleModel object with a visitor and copy constructors - needed to be able to cancel screen
        return col;
    }

    private List<InterpolationVariable> cloneVariables(List<InterpolationVariable> variables) {
        List<InterpolationVariable> clone = new ArrayList<InterpolationVariable>();
        for ( InterpolationVariable variable : variables ) {
            clone.add( cloneVariable( variable ) );
        }
        return clone;
    }

    private InterpolationVariable cloneVariable(InterpolationVariable variable) {
        InterpolationVariable clone = new InterpolationVariable( variable.getVarName(),
                                                                 variable.getDataType(),
                                                                 variable.getFactType(),
                                                                 variable.getFactField() );
        return clone;
    }

    public void onTemplateVariablesChanged(TemplateVariablesChangedEvent event) {
        variables = getDefinedVariables( event.getModel() );
        cmdApplyChanges.setEnabled( variables.size() > 0 );
    }

    private Map<InterpolationVariable, Integer> getDefinedVariables(RuleModel ruleModel) {
        Map<InterpolationVariable, Integer> variables = new HashMap<InterpolationVariable, Integer>();
        RuleModelVisitor rmv = new RuleModelVisitor( ruleModel,
                                                     variables );
        rmv.visitRuleModel( ruleModel );
        return variables;
    }

}