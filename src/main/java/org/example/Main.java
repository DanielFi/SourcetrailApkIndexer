package org.example;

import com.sourcetrail.sourcetraildb;
import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.iface.MultiDexContainer;

import java.io.File;
import java.io.IOException;

public class Main {
    public static void main(String[] args) {
        try {
            System.out.println("Hello world!");

            MultiDexContainer container = DexFileFactory.loadDexContainer(new File(args[0]), null);

            sourcetraildb.open(args[1]);
            sourcetraildb.clear();
            sourcetraildb.beginTransaction();

            Indexer indexer = new Indexer();
            indexer.index(container);

            sourcetraildb.commitTransaction();
            sourcetraildb.close();

        } catch (IOException e) {
            System.out.println(e.toString());
        }
    }
}