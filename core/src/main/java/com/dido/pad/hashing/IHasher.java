package com.dido.pad.hashing;

/**
 * Created by dido-ubuntu on 08/03/16.
 */
public interface IHasher<S> {

    void addServer( S s );

    void removeServer(S s);

}


