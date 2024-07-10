package org.example;

import com.sourcetrail.DefinitionKind;
import com.sourcetrail.ReferenceKind;
import com.sourcetrail.SymbolKind;
import com.sourcetrail.sourcetraildb;

import org.jf.dexlib2.ReferenceType;
import org.jf.dexlib2.iface.*;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jf.dexlib2.iface.instruction.ReferenceInstruction;
import org.jf.dexlib2.iface.reference.MethodReference;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Indexer {
//    private Map<String, Integer> classSymbols = new HashMap<>();
    private Map<NameHierarchy, Integer> symbolsByName = new HashMap<>();

    private int currentFileSymbol;

    public void index(MultiDexContainer container) {
        try {
            container.getDexEntryNames().forEach(new Consumer<String>() {
                @Override
                public void accept(String s) {
                    try {
                        currentFileSymbol = sourcetraildb.recordFile(s);
                        sourcetraildb.recordFileLanguage(currentFileSymbol, "dex");
                        visitDexFile(Objects.requireNonNull(container.getEntry(s)).getDexFile());
                    } catch (IOException e) {

                    }
                }
            });
        } catch (IOException e) {}
    }

    private void visitDexFile(DexFile dexFile) {;
        for (ClassDef classDef : dexFile.getClasses()) visitClassDef(classDef);
    }

    private void visitClassDef(ClassDef classDef) {
        String type = classDef.getType();
//        System.out.println(type);
        NameHierarchy name = nameForType(type);
        int symbolId = symbolsByName.computeIfAbsent(name, new Function<NameHierarchy , Integer>() {
            @Override
            public Integer apply(NameHierarchy n) {
                String recordJson = "{\"name_delimiter\": \".\", \"name_elements\": " + n.toJson() + "}";
                int id = sourcetraildb.recordSymbol(recordJson);
//                System.out.println(recordJson);
                sourcetraildb.recordSymbolKind(id, SymbolKind.SYMBOL_CLASS);
                sourcetraildb.recordSymbolDefinitionKind(id, DefinitionKind.DEFINITION_EXPLICIT);
//                sourcetraildb.recordSymbolLocation(id, currentFileSymbol, 0, 0, 0, 0);

                return id;
            }
        });

        for (Method m : classDef.getMethods()) visitMethod(m);

//        System.out.println(symbolId);
    }

    private void visitField(Field field) {

    }

    private int recordMethod(NameHierarchy name) {
        String recordJson = "{\"name_delimiter\": \".\", \"name_elements\": " + name.toJson() + "}";
        int id = sourcetraildb.recordSymbol(recordJson);
        sourcetraildb.recordSymbolKind(id, SymbolKind.SYMBOL_METHOD);
        sourcetraildb.recordSymbolDefinitionKind(id, DefinitionKind.DEFINITION_EXPLICIT);
        return id;
    }

    private void visitMethod(Method method) {
        NameHierarchy name = nameForMethod(method);

        int methodId = symbolsByName.computeIfAbsent(name, n -> {
            return recordMethod(n);
        });

        if (method.getImplementation() == null) return;

        for (Instruction instruction : method.getImplementation().getInstructions()) {
            if (instruction.getOpcode().referenceType == ReferenceType.METHOD) {
                ReferenceInstruction referenceInstruction = (ReferenceInstruction) instruction;
                MethodReference reference = (MethodReference) referenceInstruction.getReference();
                NameHierarchy referencedName = nameForType(reference.getDefiningClass());
                referencedName.appendElement(reference.getName(), reference.getReturnType(), formatStringParameters((List<String>) reference.getParameterTypes()));

                sourcetraildb.recordReference(methodId, symbolsByName.computeIfAbsent(referencedName, n -> {
                    return recordMethod(n);
                }), ReferenceKind.REFERENCE_CALL);
            }
        }
    }

    private static String[] splitType(String type) {
        return type.substring(1, type.length() - 1).split("/");
    }

    private static String prettyType(String type) {
        if (!type.startsWith("L")) return type;
        return String.join(".", splitType(type));
    }

    private static NameHierarchy nameForType(String type) {
        NameHierarchy name = new NameHierarchy();
        for (String e : splitType(type)) {
            name.appendElement(e);
        }
        return name;
    }

    private static String formatParameters(List<MethodParameter> parameters) {
        return formatStringParameters(parameters.stream().map(methodParameter -> methodParameter.getType()).collect(Collectors.toList()));
    }

    private static String formatStringParameters(List<String> parameters) {
        String result = "(";

        boolean first = true;
        for (String p : parameters) {
            if (!first) result += ", ";
            first = false;
            result += prettyType(p);
        }

        result += ")";
        return result;
    }

    private static NameHierarchy nameForMethod(Method method) {
        NameHierarchy name = nameForType(method.getDefiningClass());
        name.appendElement(method.getName(), method.getReturnType(), formatParameters((List<MethodParameter>) method.getParameters()));
        return name;
    }
}
