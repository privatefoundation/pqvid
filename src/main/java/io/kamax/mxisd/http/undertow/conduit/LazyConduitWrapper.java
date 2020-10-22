package io.kamax.mxisd.http.undertow.conduit;

import io.undertow.server.ConduitWrapper;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.ConduitFactory;
import org.xnio.conduits.Conduit;

public abstract class LazyConduitWrapper<T extends Conduit> implements ConduitWrapper<T> {

    private T conduit = null;

    protected abstract T create(ConduitFactory<T> factory, HttpServerExchange exchange);

    @Override
    public T wrap(ConduitFactory<T> factory, HttpServerExchange exchange) {
        conduit = create(factory, exchange);
        return conduit;
    }

    public T get() {
        return conduit;
    }
}
