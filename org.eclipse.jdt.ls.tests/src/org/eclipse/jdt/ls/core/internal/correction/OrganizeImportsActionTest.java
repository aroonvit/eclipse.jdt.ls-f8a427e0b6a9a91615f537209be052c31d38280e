/*******************************************************************************
 * Copyright (c) 2017-2018 Microsoft Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Microsoft Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal.correction;

import java.util.Hashtable;

import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.junit.Before;
import org.junit.Test;

public class OrganizeImportsActionTest extends AbstractQuickFixTest {

	private IJavaProject fJProject1;

	private IPackageFragmentRoot fSourceFolder;

	@Before
	public void setup() throws Exception {
		fJProject1 = newEmptyProject();
		Hashtable<String, String> options = TestOptions.getDefaultOptions();
		fJProject1.setOptions(options);
		fSourceFolder = fJProject1.getPackageFragmentRoot(fJProject1.getProject().getFolder("src"));

		this.setIgnoredKind("quickfix.*", "refactor.*");
	}

	public void setupJava9() throws Exception {
		importExistingProjects("eclipse/java9");
		IProject project = WorkspaceHelper.getProject("java9");
		fJProject1 = JavaCore.create(project);
		fSourceFolder = fJProject1.getPackageFragmentRoot(fJProject1.getProject().getFolder("src/main/java"));
	}

	@Test
	public void testOrganizeImportsModuleInfo() throws Exception {

		setupJava9();

		IPackageFragment pack1 = fSourceFolder.createPackageFragment("", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("import foo.bar.MyDriverAction;\n");
		buf.append("import java.sql.DriverAction;\n");
		buf.append("import java.sql.SQLException;\n");
		buf.append("\n");
		buf.append("module mymodule.nine {\n");
		buf.append("	requires java.sql;\n");
		buf.append("	exports foo.bar;\n");
		buf.append("	provides DriverAction with MyDriverAction;\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("module-info.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("import java.sql.DriverAction;\n");
		buf.append("\n");
		buf.append("import foo.bar.MyDriverAction;\n");
		buf.append("\n");
		buf.append("module mymodule.nine {\n");
		buf.append("	requires java.sql;\n");
		buf.append("	exports foo.bar;\n");
		buf.append("	provides DriverAction with MyDriverAction;\n");
		buf.append("}\n");

		Expected e1 = new Expected("Organize imports", buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testOrganizeImportsUnused() throws Exception {

		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("import java.util.ArrayList;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("}\n");

		Expected e1 = new Expected("Organize imports", buf.toString());

		assertCodeActions(cu, e1);
	}

	@Test
	public void testOrganizeImportsSort() throws Exception {

		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("import java.util.HashMap;\n");
		buf.append("import java.util.ArrayList;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("\n");
		buf.append("    public E() {\n");
		buf.append("        ArrayList list = new ArrayList();\n");
		buf.append("        HashMap<String, String> map = new HashMap<String, String>();\n");
		buf.append("    }\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("import java.util.ArrayList;\n");
		buf.append("import java.util.HashMap;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("\n");
		buf.append("    public E() {\n");
		buf.append("        ArrayList list = new ArrayList();\n");
		buf.append("        HashMap<String, String> map = new HashMap<String, String>();\n");
		buf.append("    }\n");
		buf.append("}\n");

		Expected e1 = new Expected("Organize imports", buf.toString());
		assertCodeActions(cu, e1);
	}

	@Test
	public void testOrganizeImportsAutomaticallyResolve() throws Exception {

		IPackageFragment pack1 = fSourceFolder.createPackageFragment("test1", false, null);

		StringBuilder buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("import java.util.ArrayList;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("\n");
		buf.append("    public E() {\n");
		buf.append("        ArrayList list = new ArrayList();\n");
		buf.append("        HashMap<String, String> map = new HashMap<String, String>();\n");
		buf.append("    }\n");
		buf.append("}\n");

		ICompilationUnit cu = pack1.createCompilationUnit("E.java", buf.toString(), false, null);

		buf = new StringBuilder();
		buf.append("package test1;\n");
		buf.append("\n");
		buf.append("import java.util.ArrayList;\n");
		buf.append("import java.util.HashMap;\n");
		buf.append("\n");
		buf.append("public class E {\n");
		buf.append("\n");
		buf.append("    public E() {\n");
		buf.append("        ArrayList list = new ArrayList();\n");
		buf.append("        HashMap<String, String> map = new HashMap<String, String>();\n");
		buf.append("    }\n");
		buf.append("}\n");

		Expected e1 = new Expected("Organize imports", buf.toString());

		assertCodeActions(cu, e1);
	}
}
