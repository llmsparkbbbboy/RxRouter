package zlc.season.rxroutercompiler;

import com.google.auto.service.AutoService;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.WildcardTypeName;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;

import zlc.season.rxrouterannotation.Router;
import zlc.season.rxrouterannotation.Provider;
import zlc.season.rxrouterannotation.Url;

import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

@AutoService(Processor.class)
public class AnnotationProcess extends AbstractProcessor {
    private String TAG = "RxRouter: ";

    private Messager messager;
    private Elements elementUtils;
    private Filer filer;

    private String className = "";
    private String packageName = "";
    private CodeBlock.Builder staticBlock = CodeBlock.builder();

    @Override
    public synchronized void init(ProcessingEnvironment processingEnvironment) {
        super.init(processingEnvironment);
        messager = processingEnv.getMessager();
        elementUtils = processingEnv.getElementUtils();
        filer = processingEnv.getFiler();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> types = new LinkedHashSet<>();
        types.add(Url.class.getCanonicalName());
        types.add(Router.class.getCanonicalName());
        return types;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }


    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnvironment) {
        Set<? extends Element> urls = roundEnvironment.getElementsAnnotatedWith(Url.class);
        Set<? extends Element> routers = roundEnvironment.getElementsAnnotatedWith(Router.class);

        generateName(routers);
        generateStaticBlock(urls);

        if (roundEnvironment.processingOver()) {
            if (packageName.isEmpty() || className.isEmpty()) {
                printError("You need to add a class that is annotated by @Router to your module!");
                return true;
            }
            try {
                generateRoutingTable();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    private void generateName(Set<? extends Element> routers) {
        for (Element element : routers) {
            packageName = elementUtils.getPackageOf(element).getQualifiedName().toString();
            className = element.getSimpleName().toString() + "Provider";
        }
    }

    private void generateRoutingTable() throws IOException {
        TypeName classWithWildcard = ParameterizedTypeName.get(ClassName.get(Class.class),
                WildcardTypeName.subtypeOf(Object.class));

        TypeName hashMap = ParameterizedTypeName.get(
                ClassName.get(Map.class),
                ParameterizedTypeName.get(String.class),
                classWithWildcard);

        FieldSpec routerTable = FieldSpec
                .builder(hashMap, "table", PUBLIC, FINAL, STATIC)
                .initializer(CodeBlock.builder().add("new $T<>()", ClassName.get(HashMap.class)).build())
                .build();

        MethodSpec provideMethod = MethodSpec.methodBuilder("provide")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(AnnotationSpec.builder(Override.class).build())
                .addParameter(String.class, "url")
                .returns(classWithWildcard)
                .addStatement("return table.get(url)")
                .build();

        TypeSpec routerTableProvider = TypeSpec.classBuilder(className)
                .addModifiers(PUBLIC, FINAL)
                .addSuperinterface(ClassName.get(Provider.class))
                .addField(routerTable)
                .addStaticBlock(staticBlock.build())
                .addMethod(provideMethod)
                .build();

        JavaFile.builder(packageName, routerTableProvider)
                .build()
                .writeTo(filer);
    }

    private void generateStaticBlock(Set<? extends Element> urlAnnotations) {
        for (Element element : urlAnnotations) {
            TypeElement typeElement = (TypeElement) element;
            ClassName activity = ClassName.get(typeElement);
            String url = element.getAnnotation(Url.class).value();
            staticBlock.add("table.put($S,$T.class);\n", url, activity);
        }
    }

    private void printError(String message) {
        messager.printMessage(Diagnostic.Kind.ERROR, TAG + message);
    }

    private void printWaring(String waring) {
        messager.printMessage(Diagnostic.Kind.WARNING, TAG + waring);
    }
}
