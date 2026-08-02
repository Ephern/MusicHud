package indi.etern.musichud.processor;

import indi.etern.musichud.interfaces.RegisterMark;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

@SupportedAnnotationTypes("indi.etern.musichud.interfaces.RegisterMark")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class RegisterMarkProcessor extends AbstractProcessor {

    private static final String REGISTRIES_RESOURCE_PREFIX = "META-INF/musichud-registries";

    private static final String CLIENT_REGISTER = "indi.etern.musichud.interfaces.ClientRegister";
    private static final String SERVER_REGISTER = "indi.etern.musichud.interfaces.ServerRegister";
    private static final String COMMON_REGISTER = "indi.etern.musichud.interfaces.CommonRegister";
    private static final String REGISTER = "indi.etern.musichud.interfaces.Register";

    private final Map<String, List<String>> registries = new LinkedHashMap<>();
    private String moduleName = "default";

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        moduleName = processingEnv.getOptions().getOrDefault("musichud.module", "default");
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            writeRegistries();
            return false;
        }

        for (Element element : roundEnv.getElementsAnnotatedWith(RegisterMark.class)) {
            if (!(element instanceof TypeElement typeElement)) {
                continue;
            }

            String qualifiedName = processingEnv.getElementUtils().getBinaryName(typeElement).toString();
            String category = determineCategory(typeElement);

            if (category != null) {
                registries.computeIfAbsent(category, k -> new ArrayList<>()).add(qualifiedName);
                processingEnv.getMessager().printMessage(
                        Diagnostic.Kind.NOTE,
                        "RegisterMarkProcessor: registered " + qualifiedName + " as " + category
                );
            }
        }

        return false;
    }

    private String determineCategory(TypeElement typeElement) {
        boolean isClient = implementsInterface(typeElement, CLIENT_REGISTER);
        boolean isServer = implementsInterface(typeElement, SERVER_REGISTER);
        boolean isCommon = implementsInterface(typeElement, COMMON_REGISTER);
        boolean isRegister = isClient || isServer || isCommon
                || implementsInterface(typeElement, REGISTER);

        if (isClient && !isServer && !isCommon) return "CLIENT";
        if (isServer && !isClient && !isCommon) return "SERVER";
        if (isCommon && !isClient && !isServer) return "COMMON";

        if (isClient || isServer || isCommon) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "@RegisterMark class " + typeElement.getQualifiedName()
                            + " implements multiple side-specific Register interfaces",
                    typeElement
            );
            return null;
        }

        if (!isRegister) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.WARNING,
                    "@RegisterMark class " + typeElement.getQualifiedName()
                            + " does not implement ClientRegister, ServerRegister, or CommonRegister",
                    typeElement
            );
            return null;
        }

        return null;
    }

    private boolean implementsInterface(TypeElement typeElement, String targetInterfaceQName) {
        for (TypeMirror iface : typeElement.getInterfaces()) {
            if (iface instanceof DeclaredType declaredType) {
                Element el = declaredType.asElement();
                if (el instanceof TypeElement te) {
                    if (te.getQualifiedName().contentEquals(targetInterfaceQName)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void writeRegistries() {
        if (registries.isEmpty()) {
            return;
        }

        String registriesResource = REGISTRIES_RESOURCE_PREFIX + "." + moduleName + ".properties";
        try {
            FileObject fileObject = processingEnv.getFiler().createResource(
                    StandardLocation.CLASS_OUTPUT,
                    "",
                    registriesResource
            );

            try (PrintWriter writer = new PrintWriter(fileObject.openWriter())) {
                for (Map.Entry<String, List<String>> entry : registries.entrySet()) {
                    String category = entry.getKey();
                    for (String className : entry.getValue()) {
                        writer.println(category + "=" + className);
                    }
                }
            }

            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.NOTE,
                    "RegisterMarkProcessor: wrote " + registries.values().stream().mapToInt(List::size).sum()
                            + " registries to " + registriesResource
            );
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "RegisterMarkProcessor: failed to write registries file: " + e.getMessage()
            );
        }
    }
}
