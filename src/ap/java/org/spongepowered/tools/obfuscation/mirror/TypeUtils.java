/*
 * This file is part of Mixin, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered <https://www.spongepowered.org>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.spongepowered.tools.obfuscation.mirror;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;

/**
 * Convenience functions for mirror types
 */
public abstract class TypeUtils {

    /**
     * Number of times to recurse into TypeMirrors when trying to determine the
     * upper bound of a TYPEVAR 
     */
    private static final int MAX_GENERIC_RECURSION_DEPTH = 5;
    
    private static final String OBJECT_SIG = "java.lang.Object";
    private static final String OBJECT_REF = "java/lang/Object";

    // No instances for you
    private TypeUtils() {}

    public static PackageElement getPackage(TypeMirror type) {
        if (!(type instanceof DeclaredType)) {
            return null;
        }
        return TypeUtils.getPackage((TypeElement)((DeclaredType)type).asElement());
    }
    
    public static PackageElement getPackage(TypeElement type) {
        Element parent = type.getEnclosingElement();
        while (parent != null && !(parent instanceof PackageElement)) {
            parent = parent.getEnclosingElement();
        }
        return (PackageElement)parent;
    }

    public static String getElementType(Element parent) {
        if (parent instanceof TypeElement) {
            return "TypeElement";
        } else if (parent instanceof ExecutableElement) {
            return "ExecutableElement";
        } else if (parent instanceof VariableElement) {
            return "VariableElement";
        } else if (parent instanceof PackageElement) {
            return "PackageElement";
        } else if (parent instanceof TypeParameterElement) {
            return "TypeParameterElement";
        }
        
        return parent.getClass().getSimpleName();
    }
    
    public static String getJavaSignature(Element element) {
        if (element instanceof ExecutableElement) {
            ExecutableElement method = (ExecutableElement)element;
            StringBuilder desc = new StringBuilder().append("(");
            boolean extra = false;
            for (VariableElement arg : method.getParameters()) {
                if (extra) {
                    desc.append(',');
                }
                desc.append(TypeUtils.getTypeName(arg.asType()));
                extra = true;
            }
            desc.append(')').append(TypeUtils.getTypeName(method.getReturnType()));
            return desc.toString();
        }
        return TypeUtils.getTypeName(element.asType());
    }

    public static String stripGenerics(String type) {
        StringBuilder sb = new StringBuilder();
        for (int pos = 0, depth = 0; pos < type.length(); pos++) {
            char c = type.charAt(pos);
            if (c == '<') {
                depth++;
            }
            if (depth == 0) {
                sb.append(c);
            } else if (c == '>') {
                depth--;
            }
        }
        return sb.toString();
    }
    
    public static String getDescriptor(Element elem) {
        if (elem instanceof ExecutableElement) {
            return TypeUtils.getDescriptor((ExecutableElement)elem);
        } else if (elem instanceof VariableElement) {
            return TypeUtils.getInternalName((VariableElement)elem);
        }
        
        return TypeUtils.getInternalName(elem.asType());
    }

    public static String getTypeName(TypeMirror type) {
        switch (type.getKind()) {
            case ARRAY:    return TypeUtils.getTypeName(((ArrayType)type).getComponentType()) + "[]";
            case DECLARED: return TypeUtils.getTypeName((DeclaredType)type);
            case TYPEVAR:  return TypeUtils.getTypeName(TypeUtils.getUpperBound(type));
            case ERROR:    return TypeUtils.OBJECT_SIG;
            default:       return type.toString();
        }
    }

    public static String getTypeName(DeclaredType type) {
        if (type == null) {
            return TypeUtils.OBJECT_SIG;
        }
        return TypeUtils.getTypeName((TypeElement)type.asElement());
    }

    private static String getTypeName(TypeElement elem) {
        return TypeUtils.getInternalName(elem).replace('/', '.');
    }
    
    public static String getName(VariableElement field) {
        return field != null ? field.getSimpleName().toString() : null;
    }
    
    public static String getName(ExecutableElement method) {
        return method != null ? method.getSimpleName().toString() : null;
    }

    public static String getDescriptor(ExecutableElement method) {
        if (method == null) {
            return null;
        }
        
        StringBuilder signature = new StringBuilder();
        
        for (VariableElement var : method.getParameters()) {
            signature.append(TypeUtils.getInternalName(var));
        }
        
        String returnType = TypeUtils.getInternalName(method.getReturnType());
        return String.format("(%s)%s", signature, returnType);
    }
    
    public static String getInternalName(VariableElement var) {
        if (var == null) {
            return null;
        }
        return TypeUtils.getInternalName(var.asType());
    }
    
    public static String getInternalName(TypeMirror type) {
        switch (type.getKind()) {
            case ARRAY:    return "[" + TypeUtils.getInternalName(((ArrayType)type).getComponentType());
            case DECLARED: return "L" + TypeUtils.getInternalName((DeclaredType)type) + ";";
            case TYPEVAR:  return "L" + TypeUtils.getInternalName(TypeUtils.getUpperBound(type)) + ";";
            case BOOLEAN:  return "Z";
            case BYTE:     return "B";
            case CHAR:     return "C";
            case DOUBLE:   return "D";
            case FLOAT:    return "F";
            case INT:      return "I";
            case LONG:     return "J";
            case SHORT:    return "S";
            case VOID:     return "V";
            // TODO figure out a better way to not crash when we get here
            case ERROR:    return "L" + TypeUtils.OBJECT_REF + ";";
            default:
        }

        throw new IllegalArgumentException("Unable to parse type symbol " + type + " with " + type.getKind() + " to equivalent bytecode type");
    }
    
    private static DeclaredType getUpperBound(TypeMirror type) {
        try {
            return TypeUtils.getUpperBound0(type, TypeUtils.MAX_GENERIC_RECURSION_DEPTH);
        } catch (IllegalStateException ex) {
            throw new IllegalArgumentException("Type symbol \"" + type + "\" is too complex", ex);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unable to compute upper bound of type symbol " + type, ex);
        }
    }

    private static DeclaredType getUpperBound0(TypeMirror type, int depth) {
        if (depth == 0) {
            throw new IllegalStateException("Generic symbol \"" + type + "\" is too complex, exceeded "
                    + TypeUtils.MAX_GENERIC_RECURSION_DEPTH + " iterations attempting to determine upper bound");
        }
        if (type instanceof DeclaredType) {
            return (DeclaredType)type;
        }
        if (type instanceof TypeVariable) {
            try {
                TypeMirror upper = ((TypeVariable)type).getUpperBound();
                return TypeUtils.getUpperBound0(upper, --depth);
            } catch (IllegalStateException ex) {
                throw ex;
            } catch (IllegalArgumentException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new IllegalArgumentException("Unable to compute upper bound of type symbol " + type);
            }
        }
        return null;
    }

    public static String getInternalName(DeclaredType type) {
        if (type == null) {
            return TypeUtils.OBJECT_REF;
        }
        return TypeUtils.getInternalName((TypeElement)type.asElement());
    }

    public static String getInternalName(TypeElement elem) {
        if (elem == null) {
            return null;
        }
        StringBuilder reference = new StringBuilder();
        reference.append(elem.getSimpleName());
        Element parent = elem.getEnclosingElement();
        while (parent != null) {
            if (parent instanceof TypeElement) {
                reference.insert(0, "$").insert(0, parent.getSimpleName());
            } else if (parent instanceof PackageElement) {
                reference.insert(0, "/").insert(0, ((PackageElement)parent).getQualifiedName().toString().replace('.', '/'));
            }
            parent = parent.getEnclosingElement();
        }
        return reference.toString();
    }

    public static boolean isAssignable(ProcessingEnvironment processingEnv, TypeMirror targetType, TypeMirror superClass) {
        boolean assignable = processingEnv.getTypeUtils().isAssignable(targetType, superClass);
        if (!assignable && targetType instanceof DeclaredType && superClass instanceof DeclaredType) {
            TypeMirror rawTargetType = TypeUtils.toRawType(processingEnv, (DeclaredType)targetType);
            TypeMirror rawSuperType = TypeUtils.toRawType(processingEnv, (DeclaredType)superClass);
            return processingEnv.getTypeUtils().isAssignable(rawTargetType, rawSuperType);
        }
        
        return assignable;
    }

    private static TypeMirror toRawType(ProcessingEnvironment processingEnv, DeclaredType targetType) {
        return processingEnv.getElementUtils().getTypeElement(((TypeElement)targetType.asElement()).getQualifiedName()).asType();
    }
    
}
