/*
 * Copyright 2018 National Bank of Belgium
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
package demetra.ui.datatransfer;

import demetra.design.VisibleForTesting;
import demetra.timeseries.TsData;
import demetra.timeseries.Ts;
import demetra.timeseries.TsCollection;
import demetra.timeseries.TsInformationType;
import demetra.ui.beans.ListenableBean;
import demetra.ui.beans.PropertyChangeSource;
import ec.util.various.swing.OnEDT;
import internal.ui.datatransfer.Magic;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.FlavorEvent;
import java.awt.datatransfer.FlavorListener;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import static java.util.Objects.requireNonNull;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import nbbrd.io.function.IOFunction;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.openide.util.Lookup;

/**
 * A support class that deals with the clipboard. It allows the user to get/set
 * time series, collections, matrixes and tables from/to any transferable. The
 * actual conversion is done by TssTransferHandler.
 * <p>
 * Note that this class can be extended to modify its behavior.
 *
 * @author Philippe Charles
 */
@lombok.extern.java.Log
public final class DataTransfer extends ListenableBean implements PropertyChangeSource {

    /**
     * A convenient method to get the current single instance of DataTransfer.
     * You could use the default lookup to get the same result.
     *
     * @return a non-null DataTransfer
     */
    @NonNull
    public static DataTransfer getDefault() {
        return INSTANCE;
    }

    private static final DataTransfer INSTANCE = new DataTransfer();

    public static final String VALID_CLIPBOARD_PROPERTY = "validClipboard";

    private final ClipboardValidator clipboardValidator;
    private final Lookup lookup;
    private final Logger logger;
    private boolean validClipboard;

    public DataTransfer() {
        this(Lookup.getDefault(), log, false);
        clipboardValidator.register(Toolkit.getDefaultToolkit().getSystemClipboard());
    }

    @VisibleForTesting
    DataTransfer(Lookup lookup, Logger logger, boolean validClipboard) {
        this.clipboardValidator = new ClipboardValidator();
        this.lookup = lookup;
        this.logger = logger;
        this.validClipboard = validClipboard;
    }

    private void setValidClipboard(boolean validClipboard) {
        boolean old = this.validClipboard;
        this.validClipboard = validClipboard;
        firePropertyChange(VALID_CLIPBOARD_PROPERTY, old, this.validClipboard);
    }

    private Stream<? extends DataTransferSpi> lookupAll() {
        return lookup.lookupAll(DataTransferSpi.class).stream();
    }

    @OnEDT
    public boolean canImport(@NonNull DataFlavor... dataFlavors) {
        // multiFlavor means "maybe", not "yes"
        return DataTransfers.isMultiFlavor(dataFlavors) || lookupAll().anyMatch(onDataFlavors(dataFlavors));
    }

    @OnEDT
    public boolean canImport(@NonNull Transferable transferable) {
        Set<DataFlavor> dataFlavors = DataTransfers.getMultiDataFlavors(transferable).collect(Collectors.toSet());
        return lookupAll().anyMatch(onDataFlavors(dataFlavors));
    }

    /**
     * Creates a Transferable from a time series.
     *
     * @param ts a non-null {@link Ts}
     * @return a never-null {@link Transferable}
     */
    @OnEDT
    @NonNull
    public Transferable fromTs(@NonNull Ts ts) {
        requireNonNull(ts);
        return fromTsCollection(TsCollection.of(ts));
    }

    /**
     * Creates a Transferable from a collection of time series.
     *
     * @param col a non-null {@link TsCollection}
     * @return a never-null {@link Transferable}
     */
    @OnEDT
    @NonNull
    public Transferable fromTsCollection(@NonNull TsCollection col) {
        requireNonNull(col);
        return asTransferable(col, lookupAll(), TsCollectionHelper.INSTANCE);
    }

    /**
     * Creates a Transferable from a TsData.
     *
     * @param data a non-null {@link TsData}
     * @return a never-null {@link Transferable}
     */
    @OnEDT
    @NonNull
    public Transferable fromTsData(@NonNull TsData data) {
        requireNonNull(data);
        return fromTs(Ts.builder().data(data).build());
    }

    /**
     * Checks if a transferable represents time series (to avoid useless loading
     * of Ts).
     *
     * @param transferable
     * @return
     */
    public boolean isTssTransferable(@NonNull Transferable transferable) {
        return transferable.isDataFlavorSupported(LocalObjectDataTransfer.DATA_FLAVOR);
    }

    /**
     * Checks if the clipboard currently contains data that can be imported.
     *
     * @return true if the data in the clipboard is importable; false otherwise
     */
    @OnEDT
    public boolean isValidClipboard() {
        return validClipboard;
    }

    /**
     * Retrieves a time series from a transferable.
     * <p>
     * Note that the content of this {@link Ts} might be asynchronous.
     * Therefore, you should call {@link Ts#load(ec.tss.TsInformationType)} or
     * {@link Ts#query(ec.tss.TsInformationType)} after this method.
     *
     * @param transferable a non-null object
     * @return an optional {@link Ts}
     */
    @OnEDT
    @NonNull
    public Optional<Ts> toTs(@NonNull Transferable transferable) {
        return toTsCollection(transferable)
                .map(TsCollection::getData)
                .filter(o -> !o.isEmpty())
                .map(o -> o.get(0));
    }

    /**
     * Retrieves a collection of time series from a transferable.
     * <p>
     * Note that the content of this {@link TsCollection} might be asynchronous.
     * Therefore, you should call
     * {@link TsCollection#load(ec.tss.TsInformationType)} or
     * {@link TsCollection#query(ec.tss.TsInformationType)} after this method.
     *
     * @param transferable a non-null object
     * @return an optional {@link TsCollection}
     */
    @OnEDT
    @NonNull
    public Optional<TsCollection> toTsCollection(@NonNull Transferable transferable) {
        requireNonNull(transferable);
        return lookupAll()
                .filter(onDataFlavors(transferable.getTransferDataFlavors()))
                .map(o -> toTsCollection(o, transferable, logger))
                .filter(Objects::nonNull)
                .findFirst();
    }

    @OnEDT
    @NonNull
    public Stream<TsCollection> toTsCollectionStream(@NonNull Transferable transferable) {
        return DataTransfers.getMultiTransferables(transferable)
                .map(this::toTsCollection)
                .filter(Optional::isPresent)
                .map(Optional::get);
    }

    /**
     * Retrieves a TsData from a transferable.
     *
     * @param transferable a non-null object
     * @return an optional {@link TsData}
     */
    @OnEDT
    @NonNull
    public Optional<TsData> toTsData(@NonNull Transferable transferable) {
        return toTs(transferable)
                .map(ts -> Magic.load(ts, TsInformationType.Data).getData());
    }

    private final class ClipboardValidator implements FlavorListener {

        private boolean isValid(Clipboard clipboard) {
            try {
                return canImport(clipboard.getAvailableDataFlavors());
            } catch (IllegalStateException ex) {
                logger.log(Level.FINE, "While getting content from clipboard", ex);
                return true; // means "maybe", not "yes"
            }
        }

        @Override
        public void flavorsChanged(FlavorEvent e) {
            setValidClipboard(isValid((Clipboard) e.getSource()));
        }

        public void register(Clipboard clipboard) {
            clipboard.addFlavorListener(this);
            validClipboard = isValid(clipboard);
        }
    }

    private static void logUnexpected(Logger logger, DataTransferSpi o, RuntimeException unexpected, String context) {
        logger.log(Level.INFO, "Unexpected exception while " + context + " using '" + getIdOrClassName(o) + "'", unexpected);
    }

    private static void logExpected(Logger logger, DataTransferSpi o, Exception expected, String context) {
        logger.log(Level.FINE, "While " + context + " using '" + getIdOrClassName(o) + "'", expected);
    }

    private static String getIdOrClassName(DataTransferSpi handler) {
        try {
            return requireNonNull(handler.getName());
        } catch (RuntimeException unexpected) {
            return handler.getClass().getName();
        }
    }

    private static DataFlavor getDataFlavorOrNull(DataTransferSpi handler) {
        try {
            return handler.getDataFlavor();
        } catch (RuntimeException unexpected) {
            return null;
        }
    }

    private static TsCollection toTsCollection(DataTransferSpi o, Transferable t, Logger logger) {
        try {
            Object data = t.getTransferData(requireNonNull(o.getDataFlavor()));
            if (o.canImportTsCollection(data)) {
                return requireNonNull(o.importTsCollection(data));
            }
        } catch (UnsupportedFlavorException | IOException ex) {
            logExpected(logger, o, ex, "getting collection");
        } catch (RuntimeException ex) {
            logUnexpected(logger, o, ex, "getting collection");
        }
        return null;
    }

    @OnEDT
    private static <T> Transferable asTransferable(T data, Stream<? extends DataTransferSpi> allHandlers, TypeHelper<T> helper) {
        return new MultiTransferable<>(
                getHandlersByFlavor(data, allHandlers, helper),
                getTransferDataLoader(data, helper));
    }

    private static <T> Map<DataFlavor, List<DataTransferSpi>> getHandlersByFlavor(T data, Stream<? extends DataTransferSpi> allHandlers, TypeHelper<T> helper) {
        return allHandlers
                .filter(o -> helper.canTransferData(data, o))
                .collect(Collectors.groupingBy(DataTransfer::getDataFlavorOrNull));
    }

    private static <T> IOFunction<DataTransferSpi, Object> getTransferDataLoader(T data, TypeHelper<T> helper) {
        return o -> helper.getTransferData(data, o);
    }

    private interface TypeHelper<T> {

        boolean canTransferData(T data, DataTransferSpi handler);

        Object getTransferData(T data, DataTransferSpi handler) throws IOException;
    }

    private static final class TsCollectionHelper implements TypeHelper<TsCollection> {

        private static final TsCollectionHelper INSTANCE = new TsCollectionHelper();

        @Override
        public boolean canTransferData(TsCollection data, DataTransferSpi handler) {
            return handler.canExportTsCollection(data);
        }

        @Override
        public Object getTransferData(TsCollection data, DataTransferSpi handler) throws IOException {
            return handler.exportTsCollection(data);
        }
    }

    private static Predicate<DataTransferSpi> onDataFlavors(DataFlavor[] dataFlavors) {
        HashSet<DataFlavor> set = new HashSet<>();
        for (DataFlavor o : dataFlavors) {
            set.add(o);
        }
        return onDataFlavors(set);
    }

    private static Predicate<DataTransferSpi> onDataFlavors(Set<DataFlavor> dataFlavors) {
        return o -> dataFlavors.contains((DataFlavor) (o != null ? getDataFlavorOrNull(o) : null));
    }
}