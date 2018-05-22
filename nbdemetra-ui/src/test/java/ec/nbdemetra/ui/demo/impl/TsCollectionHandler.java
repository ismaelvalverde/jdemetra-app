/*
 * Copyright 2013 National Bank of Belgium
 * 
 * Licensed under the EUPL, Version 1.1 or - as soon they will be approved 
 * by the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 * 
 * http://ec.europa.eu/idabc/eupl
 * 
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and 
 * limitations under the Licence.
 */
package ec.nbdemetra.ui.demo.impl;

import demetra.ui.TsManager;
import demetra.ui.components.HasTsAction;
import demetra.ui.components.HasTsCollection;
import demetra.ui.components.HasTsCollection.TsUpdateMode;
import ec.nbdemetra.ui.DemetraUiIcon;
import ec.nbdemetra.ui.demo.DemoComponentHandler;
import ec.nbdemetra.ui.demo.DemoTsActions;
import ec.tss.TsCollection;
import ec.tss.TsCollectionInformation;
import ec.tss.TsInformationType;
import ec.tss.TsStatus;
import ec.tss.tsproviders.DataSet;
import ec.tss.tsproviders.DataSource;
import ec.tss.tsproviders.IDataSourceProvider;
import ec.tstoolkit.timeseries.simplets.TsFrequency;
import ec.tstoolkit.timeseries.simplets.TsPeriod;
import internal.ui.components.HasTsCollectionCommands;
import ec.util.list.swing.JLists;
import ec.util.various.swing.FontAwesome;
import ec.util.various.swing.JCommand;
import ec.util.various.swing.ext.FontAwesomeUtils;
import internal.RandomTsBuilder;
import demetra.ui.components.TsSelectionBridge;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.BeanInfo;
import java.io.IOException;
import java.util.Date;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.swing.*;
import org.openide.awt.DropDownButtonFactory;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author Philippe Charles
 */
@ServiceProvider(service = DemoComponentHandler.class)
public final class TsCollectionHandler extends DemoComponentHandler.InstanceOf<HasTsCollection> {

    private static final RandomTsBuilder BUILDER = new RandomTsBuilder();

    private final TsCollectionInformation col;

    public TsCollectionHandler() {
        super(HasTsCollection.class);
        col = new TsCollectionInformation();

        int nbrYears = 3;

        BUILDER.withForecastCount(2);
        EnumSet.complementOf(EnumSet.of(TsFrequency.Undefined)).forEach((o) -> {
            col.items.add(BUILDER.withStart(new TsPeriod(o, 2010, 0)).withObsCount(nbrYears * o.intValue()).withName(o.name()).build());
        });

        BUILDER.withStart(new TsPeriod(TsFrequency.Monthly, 2010, 0));
        col.items.add(BUILDER.withObsCount(1).withMissingCount(0).withName("Single").build());
        col.items.add(BUILDER.withObsCount(nbrYears * 12).withMissingCount(3).withName("Missing").build());
        col.items.add(BUILDER.withObsCount(0).withMissingCount(0).withName("Empty").build());
        col.items.add(BUILDER.withStatus(TsStatus.Invalid).withName("Invalid").build());
        col.items.add(BUILDER.withStatus(TsStatus.Undefined).withName("Undefined").build());
    }

    @Override
    public void doConfigure(HasTsCollection c) {
        col.items.forEach(o -> c.getTsCollection().add(o.toTs()));
        if (c instanceof HasTsAction) {
            ((HasTsAction) c).setTsAction(DemoTsActions.SHOW_DIALOG);
        }
    }

    @Override
    public void doFillToolBar(JToolBar toolBar, HasTsCollection c) {
        toolBar.add(createFakeProviderButton(c));
        toolBar.add(createAddButton(c));
        toolBar.add(createRemoveButton(c));
        toolBar.add(createUpdateModeButton(c));
        toolBar.add(createSelectionButton(c));
        toolBar.add(createSizeLabel(c));
    }

    static JButton createAddButton(final HasTsCollection view) {
        JMenu menu = new JMenu();
        for (int i = 10; i < 10000; i *= 10) {
            menu.add(new AddRandomCommand(i).toAction(view)).setText(Integer.toString(i));
        }
        menu.add(new AddCustomCommand().toAction(view)).setText("Custom...");
        JButton result = DropDownButtonFactory.createDropDownButton(DemetraUiIcon.LIST_ADD_16, menu.getPopupMenu());
        result.addActionListener(new AddRandomCommand(1).toAction(view));
        return result;
    }

    static final class AddRandomCommand extends JCommand<HasTsCollection> {

        private final int size;
        private final TsPeriod startPeriod;

        public AddRandomCommand(int size) {
            this.size = size;
            this.startPeriod = new TsPeriod(TsFrequency.Monthly, new Date());
        }

        @Override
        public void execute(HasTsCollection c) throws Exception {
            BUILDER
                    .withObsCount(24)
                    .withStart(startPeriod)
                    .withStatus(TsStatus.Valid)
                    .withForecastCount(3)
                    .withMissingCount(0);
            c.getTsCollection().append(
                    IntStream.range(0, size)
                    .mapToObj(o -> BUILDER.withName("S" + o).build().toTs())
                    .collect(Collectors.toList())
            );
        }
    }

    static final class AddCustomCommand extends JCommand<HasTsCollection> {

        private final AddTsCollectionPanel panel;

        public AddCustomCommand() {
            this.panel = new AddTsCollectionPanel();
            panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        }

        @Override
        public void execute(HasTsCollection c) throws Exception {
            if (JOptionPane.showConfirmDialog((Component) c, panel, "Add time series", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
                BUILDER
                        .withObsCount(panel.getObsCount())
                        .withStart(panel.getStartPeriod())
                        .withStatus(panel.getTsStatus())
                        .withForecastCount(panel.getForecastCount())
                        //                        .withNaming(panel.getNaming())
                        .withMissingCount(panel.getMissingValues());
                IntStream.range(0, panel.getSeriesCount())
                        .mapToObj(o -> BUILDER.withName("S" + o).build().toTs())
                        .forEach(c.getTsCollection()::add);
            }
        }
    }

    static JButton createRemoveButton(HasTsCollection view) {
        JMenu menu = new JMenu();
        menu.add(HasTsCollectionCommands.clear().toAction(view)).setText("Clear");
        JButton result = DropDownButtonFactory.createDropDownButton(DemetraUiIcon.LIST_REMOVE_16, menu.getPopupMenu());
        result.addActionListener(RemoveLastCommand.INSTANCE.toAction(view));
        return result;
    }

    static final class RemoveLastCommand extends JCommand<HasTsCollection> {

        public static final RemoveLastCommand INSTANCE = new RemoveLastCommand();

        @Override
        public ActionAdapter toAction(HasTsCollection component) {
            return super.toAction(component).withWeakPropertyChangeListener((Component) component, HasTsCollection.TS_COLLECTION_PROPERTY);
        }

        @Override
        public boolean isEnabled(HasTsCollection component) {
            return !component.getTsCollection().isEmpty();
        }

        @Override
        public void execute(HasTsCollection component) throws Exception {
            component.getTsCollection().removeAt(component.getTsCollection().getCount() - 1);
        }
    }

    static JButton createUpdateModeButton(final HasTsCollection view) {
        final JMenu menu = new JMenu();
        for (TsUpdateMode o : TsUpdateMode.values()) {
            menu.add(new JCheckBoxMenuItem(ApplyTsUpdateModeCommand.VALUES.get(o).toAction(view))).setText(o.name());
        }
        return DropDownButtonFactory.createDropDownButton(DemetraUiIcon.TABLE_RELATION_16, menu.getPopupMenu());
    }

    static final class ApplyTsUpdateModeCommand extends JCommand<HasTsCollection> {

        public static final EnumMap<TsUpdateMode, ApplyTsUpdateModeCommand> VALUES;

        static {
            VALUES = new EnumMap<>(TsUpdateMode.class);
            for (TsUpdateMode o : TsUpdateMode.values()) {
                VALUES.put(o, new ApplyTsUpdateModeCommand(o));
            }
        }

        private final TsUpdateMode value;

        private ApplyTsUpdateModeCommand(TsUpdateMode value) {
            this.value = value;
        }

        @Override
        public ActionAdapter toAction(HasTsCollection component) {
            return super.toAction(component).withWeakPropertyChangeListener((Component) component, HasTsCollection.UDPATE_MODE_PROPERTY);
        }

        @Override
        public boolean isSelected(HasTsCollection component) {
            return component.getTsUpdateMode() == value;
        }

        @Override
        public void execute(HasTsCollection component) throws Exception {
            component.setTsUpdateMode(value);
        }
    }

    static JButton createSelectionButton(final HasTsCollection view) {
        final Action[] selectionActions = {
            new AbstractAction("All") {
                @Override
                public void actionPerformed(ActionEvent e) {
                    view.getTsSelectionModel().setSelectionInterval(0, view.getTsCollection().getCount());
                }
            },
            new AbstractAction("None") {
                @Override
                public void actionPerformed(ActionEvent e) {
                    view.getTsSelectionModel().clearSelection();
                }
            },
            new AbstractAction("Alternate") {
                @Override
                public void actionPerformed(ActionEvent e) {
                    view.getTsSelectionModel().clearSelection();
                    for (int i = 0; i < view.getTsCollection().getCount(); i += 2) {
                        view.getTsSelectionModel().addSelectionInterval(i, i);
                    }
                }
            },
            new AbstractAction("Inverse") {
                @Override
                public void actionPerformed(ActionEvent e) {
                    int[] selection = IntStream
                            .range(0, view.getTsCollection().getCount())
                            .filter(i -> !view.getTsSelectionModel().isSelectedIndex(i))
                            .toArray();
                    view.getTsSelectionModel().clearSelection();
                    IntStream.of(selection).forEach(i -> view.getTsSelectionModel().addSelectionInterval(i, i));
                }
            }
        };
        JPopupMenu menu = new JPopupMenu();
        for (Action o : selectionActions) {
            menu.add(o);
        }
        JButton result = DropDownButtonFactory.createDropDownButton(DemetraUiIcon.PUZZLE_16, menu);
        result.addActionListener(new ActionListener() {
            int i = 0;

            @Override
            public void actionPerformed(ActionEvent e) {
                selectionActions[i++ % selectionActions.length].actionPerformed(e);
            }
        });
        return result;
    }

    static JLabel createSizeLabel(final HasTsCollection view) {
        final JLabel result = new JLabel(" [0/0] ");
        if (view instanceof JComponent) {
            ((JComponent) view).addPropertyChangeListener(evt -> {
                switch (evt.getPropertyName()) {
                    case TsSelectionBridge.TS_SELECTION_PROPERTY:
                        result.setText(" [" + JLists.getSelectionIndexSize(view.getTsSelectionModel()) + "/" + view.getTsCollection().getCount() + "] ");
                        break;
                }
            });
        }
        return result;
    }

    static JButton createFakeProviderButton(HasTsCollection view) {
        JMenu menu = new JMenu();
        TsManager.getDefault().all()
                .filter(IDataSourceProvider.class::isInstance)
                .map(IDataSourceProvider.class::cast)
                .forEach(provider -> {
                    for (DataSource dataSource : provider.getDataSources()) {
                        JMenu subMenu = new JMenu(provider.getDisplayName(dataSource));
                        subMenu.setIcon(getIcon(FontAwesome.FA_FOLDER));
                        JMenuItem all = subMenu.add(new AddDataSourceCommand(dataSource).toAction(view));
                        all.setText("All");
                        all.setIcon(getIcon(FontAwesome.FA_FOLDER));
                        subMenu.addSeparator();
                        try {
                            for (DataSet dataSet : provider.children(dataSource)) {
                                JMenuItem item = subMenu.add(new AddDataSetCommand(dataSet).toAction(view));
                                item.setText(provider.getDisplayNodeName(dataSet));
                                item.setIcon(getIcon(FontAwesome.FA_LINE_CHART));
                            }
                        } catch (IOException ex) {
                            subMenu.add(ex.getMessage()).setIcon(getIcon(FontAwesome.FA_EXCLAMATION_CIRCLE));
                        }
                        menu.add(subMenu);
                    }
                });
        menu.add(new AddDataSourceCommand(DataSource.of("Missing", "")).toAction(view)).setText("Missing provider");
        JButton result = DropDownButtonFactory.createDropDownButton(getIcon(FontAwesome.FA_DATABASE), menu.getPopupMenu());
        result.setToolTipText("Data sources");
        return result;
    }

    static Icon getIcon(FontAwesome fa) {
        return FontAwesomeUtils.getIcon(fa, BeanInfo.ICON_COLOR_16x16);
    }

    static final class AddDataSourceCommand extends JCommand<HasTsCollection> {

        private final DataSource dataSource;

        public AddDataSourceCommand(DataSource dataSource) {
            this.dataSource = dataSource;
        }

        @Override
        public void execute(HasTsCollection component) throws Exception {
            TsCollection col = TsManager.getDefault().getTsCollection(dataSource, TsInformationType.Definition).get();
            col.query(TsInformationType.All);
            component.getTsCollection().append(col);
        }
    }

    static final class AddDataSetCommand extends JCommand<HasTsCollection> {

        private final DataSet dataSet;

        public AddDataSetCommand(DataSet dataSet) {
            this.dataSet = dataSet;
        }

        @Override
        public void execute(HasTsCollection component) throws Exception {
            TsCollection col = TsManager.getDefault().getTsCollection(dataSet, TsInformationType.Definition).get();
            col.query(TsInformationType.All);
            component.getTsCollection().append(col);
        }
    }
}
