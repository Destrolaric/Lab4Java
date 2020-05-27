package com.company;

import info.kgeorgiy.java.advanced.implementor.Impler;
import info.kgeorgiy.java.advanced.implementor.ImplerException;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 *
 */

public class Implementor implements Impler {
    private static Class<?> getFromArray(@NotNull Class<?> arrayType) {  //get type of element from given arr
        if (arrayType.getComponentType().isArray()) {
            return getFromArray(arrayType.getComponentType());
        } else {
            return arrayType.getComponentType();
        }
    }

    public static @NotNull String getPackageName(@NotNull Class<?> token) {
        if (token.getPackage() == null) {
            return "";
        } else {
            return token.getPackage().getName();
        }
    }

    private static @NotNull String setDefault(@NotNull Class<?> type) {  // gives default values for given types
        if (type.isPrimitive()) {
            if (Boolean.TYPE.equals(type)) {
                return "false";
            } else if (Void.TYPE.equals(type)) {
                return "";
            } else {
                return "0";
            }
        } else {
            return "null";
        }
    }

    private @NotNull Set<Class<?>> findUsedClasses(Class<?> token) {   //detect all used classes from same package and standard classes
        Set<Class<?>> classes = new HashSet<>();
        for (Method method : getMethods(token)) {
            for (Class<?> paramType : method.getParameterTypes()) {
                if (paramType.isArray()) {
                    Class<?> cls = getFromArray(paramType);
                    if (!cls.isPrimitive()) {
                        classes.add(cls);
                    }
                } else if (!paramType.isPrimitive()
                        && !paramType.getPackage().getName().startsWith("java.lang")
                        && !paramType.getPackage().getName().equals(token.getPackage().getName())
                ) {
                    classes.add(paramType);
                }
            }
            if (method.getReturnType().isArray()) {
                Class<?> cls = getFromArray(method.getReturnType());
                if (!cls.isPrimitive()) {
                    classes.add(cls);
                }
            } else if (!method.getReturnType().isPrimitive()
                    && !method.getReturnType().getPackage().getName().startsWith("java.lang")
                    && !method.getReturnType().getPackage().getName().equals(token.getPackage().getName())) {
                classes.add(method.getReturnType());
            }

            for (Class<?> e : method.getExceptionTypes()) {
                if (e.isArray()) {
                    Class<?> cls = getFromArray(e);
                    if (!cls.isPrimitive()) {
                        classes.add(cls);
                    }
                } else if (!e.isPrimitive()
                        && !e.getPackage().getName().startsWith("java.lang")
                        && !e.getPackage().getName().equals(token.getPackage().getName())
                ) {
                    classes.add(e);
                }
            }

        }

        for (Constructor<?> constructor : token.getConstructors()) {
            for (Class<?> paramType : constructor.getParameterTypes()) {
                if (paramType.isArray()) {
                    Class<?> cls = getFromArray(paramType);
                    if (!cls.isPrimitive()) {
                        classes.add(cls);
                    }
                } else if (!paramType.isPrimitive()
                        && !paramType.getPackage().getName().startsWith("java.lang")
                        && !paramType.getPackage().getName().equals(token.getPackage().getName())
                ) {
                    classes.add(paramType);
                }
            }

            for (Class<?> e : constructor.getExceptionTypes()) {
                if (e.isArray()) {
                    Class<?> constructors = getFromArray(e);
                    if (!constructors.isPrimitive()) {
                        classes.add(constructors);
                    }
                } else if (!e.isPrimitive()
                        && !e.getPackage().getName().startsWith("java.lang")
                        && !e.getPackage().getName().equals(token.getPackage().getName())) {
                    classes.add(e);
                }
            }
        }
        return classes;
    }

    private @NotNull List<Method> getMethods(Class<?> token) {   // get methods from interface that need to implement
        List<Method> methods = new ArrayList<>();
        if (token == null) {
            return methods;
        }

        methods.addAll(getMethods(token.getSuperclass()));

        for (Class<?> interf : token.getInterfaces()) {
            methods.addAll(getMethods(interf));
        }

        for (Method m : token.getDeclaredMethods()) {

            if (Modifier.isNative(m.getModifiers())
                    || Modifier.isStatic(m.getModifiers()) || m.isSynthetic()) {
                continue;
            }

            if (Modifier.isPublic(m.getModifiers()) || Modifier.isProtected(m.getModifiers())
                    || (!Modifier.isProtected(m.getModifiers())
                    && !Modifier.isPublic(m.getModifiers())
                    && !Modifier.isPrivate(m.getModifiers()))) {
                boolean noAdding = false;
                for (int i = 0; i < methods.size(); ++i) {
                    Method pm = methods.get(i);

                    if (compareMethods(m, pm)) {
                        methods.set(i, m);
                        noAdding = true;
                        break;
                    }
                }
                if (!noAdding) {
                    methods.add(m);
                }
            }
        }
        return methods;
    }

    private boolean compareMethods(@NotNull Method m1, @NotNull Method m2) { //by signature
        if ((m1.getName().equals(m2.getName())) && m1.getParameterTypes().length == m2.getParameterTypes().length) {

            for (int i = 0; i < m1.getParameterTypes().length; ++i) {
                if (!m1.getParameterTypes()[i].getCanonicalName().equals(m2.getParameterTypes()[i].getCanonicalName())) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    StringBuilder addBody(StringBuilder text, @NotNull Class<?> token) throws ImplerException {
        addFields(text, token);
        addMethods(text, token);
        return text;

    }

    @Override
    public void implement(@NotNull Class<?> token, @NotNull Path root) throws ImplerException, IOException {
        try {
            checkPossibility(token);

            root = createPathToFile(token, root, "java");
            createDir(root);
            FileWriter writer = new FileWriter(root.toFile());
            writer.write(generateClass(token, root).toString());
            writer.close();


        } catch (NullPointerException e) {
            throw new ImplerException(e);
        }

    }

    void addHeader(@NotNull StringBuilder text, @NotNull Class<?> token) throws ImplerException {
        text.append("package ").append(token.getPackage().getName()).append(";").append("\n");
        if (Modifier.isPrivate(token.getModifiers())) {
            throw new ImplerException();
        }
        for (Class<?> c : findUsedClasses(token)) {
            text.append("import ").append(c.getCanonicalName()).append(";").append("\n");
        }
        text.append("public class ").append(token.getSimpleName()).append("Impl");
        if (token.isInterface()) {
            text.append(" implements ").append(token.getCanonicalName()).append(" {\n");
        } else {
            text.append(" extends ").append(token.getCanonicalName()).append(" {\n");
        }
    }

    private @NotNull StringBuilder addExceptions(@NotNull Method method) {
        Class<?>[] exceptions = method.getExceptionTypes();
        StringBuilder text = new StringBuilder();
        if (exceptions.length != 0) {
            text.append(" throws ");
            for (int i = 0; i < exceptions.length; ++i) {
                text.append(exceptions[i].getSimpleName());
                if (i < exceptions.length - 1) {
                    text.append(", ");
                }
            }
        }
        return text;
    }

    private @NotNull StringBuilder addMethodArgs(@NotNull Method method) {
        StringBuilder args = new StringBuilder();
        int iter = 0;
        for (Class<?> type : method.getParameterTypes()) {
            args.append(type.getCanonicalName()).append(" arg").append(iter);
            iter++;
            if (iter != method.getParameterCount()) {
                args.append(", ");
            }
        }
        return args;
    }

    private void addMethods(StringBuilder text, @NotNull Class<?> token) {
        Method[] methods = token.getMethods();
        for (Method method : methods) {
            int modifiers = method.getModifiers();

            if (Modifier.isFinal(modifiers)
                    || Modifier.isNative(modifiers)
                    || Modifier.isPrivate(modifiers)
                    || !Modifier.isAbstract(modifiers)) {
                continue;
            }
            modifiers ^= Modifier.ABSTRACT;
            if (Modifier.isTransient(modifiers)) {
                modifiers ^= Modifier.TRANSIENT;
            }

            StringBuilder letter = new StringBuilder();

            if (method.isAnnotationPresent(Override.class)) {
                letter.append("\n@Override\n");
            }

            letter.append(Modifier.toString(modifiers)).append(' ');
            Class<?> type = method.getReturnType();
            letter.append(type.getCanonicalName()).append(' ');

            letter.append(method.getName());
            letter.append("(")
                    .append(addMethodArgs(method))
                    .append(")").append(addExceptions(method))
                    .append("\n{\n").append("return ")
                    .append(setDefault(method.getReturnType())).append(";\n}\n");
            text.append(letter).append("\n");
        }
    }

    private void addFields(StringBuilder text, @NotNull Class<?> token) throws ImplerException {

        Field[] fields = token.getFields();
        for (Field field : fields) {
            field.setAccessible(true);
            try {
                String[] words = field.getType().toString().replace(";", "").split("\\.");
                String word = words[words.length - 1];
                String strValue = field.get(token).toString();
                if (!strValue.isEmpty()) {
                    if (word.equals("String")) {
                        text.append("\t")
                                .append(Modifier.toString(field.getModifiers())).append(" ")
                                .append(word).append(" ").append(field.getName())
                                .append("=\"").append(strValue).append("\";\n");
                    } else {
                        text.append("\t").append(Modifier.toString(field.getModifiers()))
                                .append(" ").append(word).append(" ")
                                .append(field.getName())
                                .append("=").append(strValue)
                                .append(";\n");
                    }
                } else {

                    text.append("\t")
                            .append(Modifier.toString(field.getModifiers()))
                            .append(" ").append(field.getType())
                            .append(" ").append(field.getName())
                            .append(";\n");
                }
                return;
            } catch (IllegalAccessException e) {
                throw new ImplerException("Access violated", e);
            }

        }

    }

    void checkPossibility(Class<?> token) throws ImplerException {
        boolean flag = true;
        for (Constructor<?> constructor : token.getDeclaredConstructors()) {
            if (!Modifier.isPrivate(constructor.getModifiers())) {
                flag = false;
                break;
            }
        }

        if (token.getDeclaredConstructors().length == 0) {
            flag = false;
        }


        if (flag || Modifier.isFinal(token.getModifiers())) {
            throw new ImplerException("You Got Bamboozled");
        }
    }


    void addConstructors(@NotNull Class<?> token, StringBuilder out) {
        Constructor<?>[] constructors = token.getDeclaredConstructors();
        boolean defaultConstructor = false;
        if (constructors.length == 0) {
            defaultConstructor = true;
        }
        for (Constructor<?> constructor : constructors) {
            if (Modifier.isPrivate(constructor.getModifiers())) {
                continue;
            }
            if (constructor.getParameterTypes().length == 0) {
                defaultConstructor = true;
                break;
            }
        }

        if (!defaultConstructor) {
            int k = 0;
            while ((Modifier.isPrivate(constructors[k].getModifiers()))) {
                ++k;
            }
            Class<?>[] params = constructors[k].getParameterTypes();
            out.append("\n");
            out.append("    public ").append(token.getSimpleName()).append("Impl").append("()");
            if (constructors[k].getExceptionTypes().length != 0) {
                out.append(" throws ");
                Class<?>[] es = constructors[k].getExceptionTypes();
                for (int i = 0; i < es.length; ++i) {
                    out.append(es[i].getSimpleName());
                    if (i < es.length - 1) {
                        out.append(", ");
                    }
                }
            }
            out.append("{").append("\n");
            out.append("        super(");
            for (int i = 0; i < params.length; ++i) {
                out.append("(").append(params[i].getSimpleName()).append(")");
                out.append(setDefault(params[i]));
                if (i < params.length - 1) {
                    out.append(", ");
                }
            }
            out.append(");").append("\n");
            out.append("    }");
            out.append("\n");

        }
    }

    public Path createPathToFile(Class<?> token, @NotNull Path path, String suffix) {
        return path.resolve(getPackageName(token)
                .replace('.', File.separatorChar))
                .resolve(String.format("%s.%s", token.getSimpleName() + "Impl", suffix));
    }

    public void createDir(@NotNull Path root) throws ImplerException {
        if (root.getParent() != null) {
            try {
                Files.createDirectories(root.getParent());
            } catch (SecurityException e) {
                throw new ImplerException("Not Enough permissions for creating directories", e);
            } catch (IOException e) {
                throw new ImplerException("Cannot create directories for output class", e);
            }
        }
    }

    StringBuilder generateClass(Class<?> token, Path root) throws ImplerException {
        StringBuilder generatedCode = new StringBuilder();
        addHeader(generatedCode, token);
        addConstructors(token, generatedCode);
        generatedCode = addBody(generatedCode, token);
        generatedCode.append("}");
        return generatedCode;


    }

}
