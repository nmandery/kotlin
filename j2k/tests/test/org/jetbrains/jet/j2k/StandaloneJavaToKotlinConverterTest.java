/*
* Copyright 2010-2013 JetBrains s.r.o.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package org.jetbrains.jet.j2k;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.testFramework.UsefulTestCase;
import junit.framework.Assert;
import junit.framework.Test;
import junit.framework.TestSuite;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.ConfigurationKind;
import org.jetbrains.jet.cli.jvm.compiler.JetCoreEnvironment;

import java.io.File;
import java.io.IOException;

import static org.jetbrains.jet.JetTestUtils.createEnvironmentWithMockJdkAndIdeaAnnotations;

@SuppressWarnings("JUnitTestCaseWithNoTests")
public class StandaloneJavaToKotlinConverterTest extends UsefulTestCase {
    private final String myDataPath;
    private final String myName;

    @SuppressWarnings("JUnitTestCaseWithNonTrivialConstructors")
    public StandaloneJavaToKotlinConverterTest(String dataPath, String name) {
        myDataPath = dataPath;
        myName = name;
    }

    @Override
    protected void runTest() throws Throwable {
        JetCoreEnvironment jetCoreEnvironment = createEnvironmentWithMockJdkAndIdeaAnnotations(getTestRootDisposable(), ConfigurationKind.JDK_ONLY);

        Converter converter = new Converter(jetCoreEnvironment.getProject());

        String javaPath = "j2k/tests/testData/" + getTestFilePath();
        String kotlinPath = javaPath.replace(".jav", ".kt");

        File kotlinFile = new File(kotlinPath);
        if (!kotlinFile.exists()) {
            FileUtil.writeToFile(kotlinFile, "");
        }
        String expected = FileUtil.loadFile(kotlinFile, true);
        File javaFile = new File(javaPath);
        String javaCode = FileUtil.loadFile(javaFile, true);

        String actual = "";
        String parentFileName = javaFile.getParentFile().getName();
        if (parentFileName.equals("expression")) {
            actual = expressionToKotlin(converter, javaCode);
        }
        else if (parentFileName.equals("statement")) {
            actual = statementToKotlin(converter, javaCode);
        }
        else if (parentFileName.equals("method")) {
            actual = methodToKotlin(converter, javaCode);
        }
        else if (parentFileName.equals("class")) {
            actual = fileToKotlin(converter, javaCode);
        }
        else if (parentFileName.equals("file")) {
            actual = fileToKotlin(converter, javaCode);
        }
        else if (parentFileName.equals("comp")) actual = fileToFileWithCompatibilityImport(javaCode);

        assert !actual.isEmpty() : "Specify what is it: file, class, method, statement or expression: " + javaPath + " parent: " + parentFileName;

        File tmp = new File(kotlinPath + ".tmp");
        if (!expected.equals(actual)) FileUtil.writeToFile(tmp, actual);
        if (expected.equals(actual) && tmp.exists()) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }

        Assert.assertEquals(expected, actual);
    }

    @NotNull
    String getTestFilePath() {
        return myDataPath + "/" + myName + ".jav";
    }


    @NotNull
    @Override
    public String getName() {
        return "test_" + myName;
    }

    @NotNull
    public static Test suite() {
        TestSuite suite = new TestSuite();
//        suite.addTest(new StandaloneJavaToKotlinConverterTest("ast/class/file", "kt-639"));
        suite.addTest(TestCaseBuilder.suiteForDirectory("j2k/tests/testData", "/ast", new TestCaseBuilder.NamedTestFactory() {
            @NotNull
            @Override
            public Test createTest(@NotNull String dataPath, @NotNull String name) {
                return new StandaloneJavaToKotlinConverterTest(dataPath, name);
            }
        }));
        return suite;
    }

    @NotNull
    private static String fileToFileWithCompatibilityImport(@NotNull String text) {
        return JavaToKotlinTranslator.generateKotlinCodeWithCompatibilityImport(text);
    }

    @NotNull
    private static String fileToKotlin(Converter converter, @NotNull String text) {
        return generateKotlinCode(converter, JavaToKotlinTranslator.createFile(converter.getProject(), text));
    }

    @NotNull
    private static String generateKotlinCode(@NotNull Converter converter, @Nullable PsiFile file) {
        if (file != null && file instanceof PsiJavaFile) {
            JavaToKotlinTranslator.setClassIdentifiers(converter, file);
            return prettify(converter.elementToKotlin(file));
        }
        return "";
    }

    @NotNull
    private static String methodToKotlin(Converter converter, String text) throws IOException {
        String result = fileToKotlin(converter, "final class C {" + text + "}")
                .replaceAll("class C\\(\\) \\{", "");
        result = result.substring(0, result.lastIndexOf("}"));
        return prettify(result);
    }

    @NotNull
    private static String statementToKotlin(Converter converter, String text) throws Exception {
        String result = methodToKotlin(converter, "void main() {" + text + "}");
        int pos = result.lastIndexOf("}");
        result = result.substring(0, pos).replaceFirst("fun main\\(\\) : Unit \\{", "");
        return prettify(result);
    }

    @NotNull
    private static String expressionToKotlin(Converter converter, String code) throws Exception {
        String result = statementToKotlin(converter, "Object o =" + code + "}");
        result = result.replaceFirst("var o : Any\\? =", "");
        return prettify(result);
    }

    @NotNull
    private static String prettify(@Nullable String code) {
        if (code == null) {
            return "";
        }
        return code
                .trim()
                .replaceAll("\r\n", "\n")
                .replaceAll(" \n", "\n")
                .replaceAll("\n ", "\n")
                .replaceAll("\n+", "\n")
                .replaceAll(" +", " ")
                .trim()
                ;
    }
}
