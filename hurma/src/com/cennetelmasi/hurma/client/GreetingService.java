package com.cennetelmasi.hurma.client;

import com.google.gwt.user.client.rpc.RemoteService;
import com.google.gwt.user.client.rpc.RemoteServiceRelativePath;

/**
 * The client side stub for the RPC service.
 */
@RemoteServiceRelativePath("greet")
public interface GreetingService extends RemoteService {
        String greetServer(String name, String pass);
        String nodeTypeName(int index);
        String nodeTypeID(int index);
        String nodeTypeMIB(int index);
        String nodeTypeNumber();
}
