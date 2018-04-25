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
package demetra.ui;

import ec.nbdemetra.core.GlobalService;
import ec.tss.ITsProvider;
import ec.tss.Ts;
import ec.tss.TsCollection;
import ec.tss.TsFactory;
import ec.tss.TsInformationType;
import ec.tss.TsMoniker;
import ec.tstoolkit.MetaData;
import ec.tstoolkit.design.NewObject;
import ec.tstoolkit.timeseries.simplets.TsData;
import java.util.ArrayList;
import java.util.List;
import java.util.Observer;
import java.util.stream.Collector;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author Philippe Charles
 */
@GlobalService
@ServiceProvider(service = TsManager.class)
public class TsManager implements AutoCloseable {

    @Nonnull
    public static TsManager getDefault() {
        return Lookup.getDefault().lookup(TsManager.class);
    }

    private final TsFactory delegate = TsFactory.instance;

    @Nonnull
    @NewObject
    public TsCollection newTsCollection() {
        return delegate.createTsCollection();
    }

    @Nonnull
    @NewObject
    public TsCollection newTsCollectionWithName(@Nullable String name) {
        return delegate.createTsCollection(name);
    }

    @Nonnull
    public TsCollection lookupTsCollection(@Nullable String name, @Nonnull TsMoniker moniker, @Nonnull TsInformationType type) {
        return delegate.createTsCollection(name, moniker, type);
    }

    @Nonnull
    @NewObject
    public Ts newTs(@Nullable String string, @Nullable MetaData md, @Nullable TsData tsdata) {
        return delegate.createTs(string, md, tsdata);
    }

    @Nonnull
    public Ts lookupTs(@Nullable String name, @Nonnull TsMoniker moniker, @Nonnull TsInformationType type) {
        return delegate.createTs(name, moniker, type);
    }

    @Nonnull
    @NewObject
    public Ts newTsWithName(@Nullable String name) {
        return delegate.createTs(name);
    }

    @Nullable
    public Ts lookupTs(@Nonnull TsMoniker moniker) {
        return delegate.getTs(moniker);
    }

    @Nonnull
    public Collector<Ts, ?, TsCollection> getTsCollector() {
        return Collector.<Ts, List<Ts>, TsCollection>of(ArrayList::new, List::add, (l, r) -> {
            l.addAll(r);
            return l;
        }, o -> delegate.createTsCollection(null, null, null, o));
    }

    public boolean register(@Nonnull ITsProvider provider) {
        return delegate.add(provider);
    }

    public void unregister(@Nonnull ITsProvider provider) {
        delegate.remove(provider.getSource());
    }

    @Override
    public void close() {
        delegate.dispose();
    }

    public void addObserver(@Nonnull Observer obs) {
        delegate.addObserver(obs);
    }

    public void removeObserver(@Nonnull Observer obs) {
        delegate.deleteObserver(obs);
    }
}