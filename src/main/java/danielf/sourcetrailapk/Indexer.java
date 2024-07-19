package danielf.sourcetrailapk;

import com.sourcetrail.DefinitionKind;
import com.sourcetrail.ReferenceKind;
import com.sourcetrail.SymbolKind;
import com.sourcetrail.sourcetraildb;

import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import net.fabricmc.mappingio.tree.VisitableMappingTree;
import org.jf.dexlib2.ReferenceType;
import org.jf.dexlib2.iface.*;
import org.jf.dexlib2.iface.instruction.DualReferenceInstruction;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jf.dexlib2.iface.instruction.ReferenceInstruction;
import org.jf.dexlib2.iface.reference.FieldReference;
import org.jf.dexlib2.iface.reference.MethodReference;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class Indexer {
//    private Map<String, Integer> classSymbols = new HashMap<>();
    private Map<NameHierarchy, Integer> symbolsByName = new HashMap<>();

    private int currentFileSymbol;

    private MappingTree mappingTree;

    public Indexer() {}

    public Indexer(String mappingPath) {
        try {
            VisitableMappingTree t = new MemoryMappingTree();
            MappingReader.read(Path.of(mappingPath), t);
            mappingTree = t;
        } catch (IOException e) {
            System.err.println("Failed to open mapping path: " + mappingPath);
        }
    }

    public void index(MultiDexContainer container) {
        try {
            container.getDexEntryNames().forEach(new Consumer<String>() {
                @Override
                public void accept(String s) {
                    try {
                        currentFileSymbol = sourcetraildb.recordFile(s);
                        sourcetraildb.recordFileLanguage(currentFileSymbol, "dex");
                        visitDexFile(Objects.requireNonNull(container.getEntry(s)).getDexFile());
                    } catch (IOException e) {}
                }
            });
        } catch (IOException e) {}
    }

    private void visitDexFile(DexFile dexFile) {
        for (ClassDef classDef : dexFile.getClasses()) visitClassDef(classDef);
    }

    private int recordClass(NameHierarchy name) {
        String recordJson = "{\"name_delimiter\": \".\", \"name_elements\": " + name.toJson() + "}";
        int id = sourcetraildb.recordSymbol(recordJson);
        sourcetraildb.recordSymbolKind(id, SymbolKind.SYMBOL_CLASS);
        sourcetraildb.recordSymbolDefinitionKind(id, DefinitionKind.DEFINITION_EXPLICIT);

        return id;
    }

    private void visitClassDef(ClassDef classDef) {
        String type = classDef.getType();
//        System.out.println(type);
        type = resolveType(type);
        NameHierarchy name = nameForType(type);
        int symbolId = symbolsByName.computeIfAbsent(name, this::recordClass);

        NameHierarchy superclass = nameForType(resolveType(classDef.getSuperclass()));
        int superclassId = symbolsByName.computeIfAbsent(superclass, this::recordClass);
        sourcetraildb.recordReference(symbolId, superclassId, ReferenceKind.REFERENCE_INHERITANCE);

        for (Method m : classDef.getMethods()) visitMethod(m);
        for (Field f: classDef.getFields()) visitField(f);

//        System.out.println(symbolId);
    }

    private int recordField(NameHierarchy name) {
        String recordJson = "{\"name_delimiter\": \".\", \"name_elements\": " + name.toJson() + "}";
        int id = sourcetraildb.recordSymbol(recordJson);
        sourcetraildb.recordSymbolKind(id, SymbolKind.SYMBOL_FIELD);
        sourcetraildb.recordSymbolDefinitionKind(id, DefinitionKind.DEFINITION_EXPLICIT);
        return id;
    }

    private void visitField(Field field) {
        NameHierarchy name = nameForField(field);

        int fieldId = symbolsByName.computeIfAbsent(name, this::recordField);
    }

    private int recordMethod(NameHierarchy name) {
        String recordJson = "{\"name_delimiter\": \".\", \"name_elements\": " + name.toJson() + "}";
        int id = sourcetraildb.recordSymbol(recordJson);
        sourcetraildb.recordSymbolKind(id, SymbolKind.SYMBOL_METHOD);
        sourcetraildb.recordSymbolDefinitionKind(id, DefinitionKind.DEFINITION_EXPLICIT);
        return id;
    }

    private void recordMethodReference(int callerId, MethodReference reference) {
        NameHierarchy referencedName = nameForMethodReference(reference);
        sourcetraildb.recordReference(callerId, symbolsByName.computeIfAbsent(referencedName, this::recordMethod), ReferenceKind.REFERENCE_CALL);
    }

    private void recordFieldReference(int callerId, FieldReference reference) {
        NameHierarchy referencedName = nameForFieldReference(reference);
        sourcetraildb.recordReference(callerId, symbolsByName.computeIfAbsent(referencedName, this::recordField), ReferenceKind.REFERENCE_USAGE);
    }

    private void visitMethod(Method method) {
        NameHierarchy name = nameForMethod(method);

        int methodId = symbolsByName.computeIfAbsent(name, this::recordMethod);

        if (method.getImplementation() == null) return;

        for (Instruction instruction : method.getImplementation().getInstructions()) {
            if (instruction.getOpcode().referenceType == ReferenceType.METHOD) {
                ReferenceInstruction referenceInstruction = (ReferenceInstruction) instruction;
                MethodReference reference = (MethodReference) referenceInstruction.getReference();
                recordMethodReference(methodId, reference);
            }

            if (instruction.getOpcode().referenceType2 == ReferenceType.METHOD) {
                DualReferenceInstruction referenceInstruction = (DualReferenceInstruction) instruction;
                MethodReference reference = (MethodReference) referenceInstruction.getReference2();
                recordMethodReference(methodId, reference);
            }

            if (instruction.getOpcode().referenceType == ReferenceType.FIELD) {
                ReferenceInstruction referenceInstruction = (ReferenceInstruction) instruction;
                FieldReference reference = (FieldReference) referenceInstruction.getReference();
                recordFieldReference(methodId, reference);
            }

            if (instruction.getOpcode().referenceType2 == ReferenceType.FIELD) {
                DualReferenceInstruction referenceInstruction = (DualReferenceInstruction) instruction;
                FieldReference reference = (FieldReference) referenceInstruction.getReference2();
                recordFieldReference(methodId, reference);
            }
        }
    }

    private String prettyType(String type) {
        return resolveType(type).replace("/", ".");
    }

    private NameHierarchy nameForType(String type) {
        NameHierarchy name = new NameHierarchy();
        for (String e : type.split("/")) {
            name.appendElement(e);
        }
        return name;
    }

    private String formatParameters(List<MethodParameter> parameters) {
        return formatStringParameters(parameters.stream().map(methodParameter -> prettyType(methodParameter.getType())).collect(Collectors.toList()));
    }

    private String formatStringParameters(List<String> parameters) {
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

    private NameHierarchy nameForMethod(Method method) {
        NameHierarchy name = nameForType(resolveType(method.getDefiningClass()));
        String methodName = method.getName();
        try {
            String className = method.getDefiningClass();
            className = className.substring(1, className.length() - 1);
            String newMethodName = mappingTree.getMethod(className, methodName, "(" + String.join("", method.getParameters()) + ")" + method.getReturnType()).getName(0);
            if (newMethodName != null) {
                methodName = newMethodName;
                System.out.println(newMethodName);
            }
        } catch (NullPointerException npe) {}
        name.appendElement(methodName, prettyType(method.getReturnType()), formatParameters((List<MethodParameter>) method.getParameters()));
        return name;
    }

    private NameHierarchy nameForMethodReference(MethodReference reference) {
        NameHierarchy name = nameForType(resolveType(reference.getDefiningClass()));
        String methodName = reference.getName();
        try {
            String className = reference.getDefiningClass();
            className = className.substring(1, className.length() - 1);
            String newMethodName = mappingTree.getMethod(className, methodName, "(" + String.join("", reference.getParameterTypes()) + ")" + reference.getReturnType()).getName(0);
            if (newMethodName != null) {
                methodName = newMethodName;
                System.out.println(newMethodName);
            }
        } catch (NullPointerException npe) {}
        name.appendElement(methodName, prettyType(reference.getReturnType()), formatStringParameters((List<String>) reference.getParameterTypes()));
        return name;
    }

    private NameHierarchy nameForField(Field field) {
        NameHierarchy name = nameForType(resolveType(field.getDefiningClass()));
        String fieldName = field.getName();
        try {
            String className = field.getDefiningClass();
            className = className.substring(1, className.length() - 1);
            String newFieldName = mappingTree.getField(className, fieldName, field.getType()).getName(0);
            if (newFieldName != null) {
                fieldName = newFieldName;
                System.out.println(newFieldName);
            }
        } catch (NullPointerException npe) {}
        name.appendElement(fieldName, prettyType(field.getType()), "");
        return name;
    }

    private NameHierarchy nameForFieldReference(FieldReference reference) {
        NameHierarchy name = nameForType(resolveType(reference.getDefiningClass()));
        String fieldName = reference.getName();
        try {
            String className = reference.getDefiningClass();
            className = className.substring(1, className.length() - 1);
            String newFieldName = mappingTree.getField(className, fieldName, reference.getType()).getName(0);
            if (newFieldName != null) {
                fieldName = newFieldName;
            }
        } catch (NullPointerException npe) {}
        name.appendElement(fieldName, prettyType(reference.getType()), "");
        return name;
    }

    private String resolveType(String type) {
        int dimension = type.lastIndexOf('[');
        if (type.charAt(dimension + 1) != 'L') return type;
        type = type.substring(1, type.length() - 1);
        try {
            String newType = mappingTree.getClass(type.substring(dimension + 1)).getName(0);
            if (newType != null) {
                System.out.println(newType);
                return "[".repeat(dimension + 1) + newType;
            }
        } catch (NullPointerException npe) {}
        return type;
    }
}
