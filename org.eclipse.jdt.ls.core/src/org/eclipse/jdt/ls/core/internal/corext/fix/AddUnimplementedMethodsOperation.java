/*******************************************************************************
 * Copyright (c) 2000, 2015 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Copied from org.eclipse.jdt.internal.corext.codemanipulation.AddUnimplementedMethodsOperation
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Mateusz Wenus <mateusz.wenus@gmail.com> - [override method] generate in declaration order [code generation] - https://bugs.eclipse.org/bugs/show_bug.cgi?id=140971
 *     Stephan Herrmann - Contribution for Bug 463360 - [override method][null] generating method override should not create redundant null annotations
 *******************************************************************************/

package org.eclipse.jdt.ls.core.internal.corext.fix;

import java.util.Arrays;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnumConstantDeclaration;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite.ImportRewriteContext;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.internal.corext.codemanipulation.CodeGenerationSettings;
import org.eclipse.jdt.internal.corext.codemanipulation.ContextSensitiveImportRewriteContext;
import org.eclipse.jdt.internal.corext.codemanipulation.StubUtility2Core;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalModelCore;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.jdt.ls.core.internal.BindingLabelProvider;
import org.eclipse.jdt.ls.core.internal.Messages;
import org.eclipse.jdt.ls.core.internal.corext.utils.MethodsSourcePositionComparator;
import org.eclipse.jdt.ls.core.internal.corrections.CorrectionMessages;
import org.eclipse.jdt.ls.core.internal.hover.JavaElementLabels;
import org.eclipse.jdt.ls.core.internal.preferences.PreferenceManager;

public class AddUnimplementedMethodsOperation extends CompilationUnitRewriteOperation {

	private ASTNode fTypeNode;

	/**
	 * Create a {@link AddUnimplementedMethodsOperation}
	 *
	 * @param typeNode
	 *            must be one of the following types:
	 *            <ul>
	 *            <li>AnonymousClassDeclaration</li>
	 *            <li>AbstractTypeDeclaration</li>
	 *            <li>EnumConstantDeclaration</li>
	 *            </ul>
	 */
	public AddUnimplementedMethodsOperation(ASTNode typeNode) {
		fTypeNode = typeNode;
	}

	@Override
	public void rewriteAST(CompilationUnitRewrite cuRewrite, LinkedProposalModelCore model) throws CoreException {
		IMethodBinding[] unimplementedMethods = getUnimplementedMethods(fTypeNode);
		if (unimplementedMethods.length == 0) {
			return;
		}

		ImportRewriteContext context = new ContextSensitiveImportRewriteContext((CompilationUnit) fTypeNode.getRoot(), fTypeNode.getStartPosition(), cuRewrite.getImportRewrite());
		ASTRewrite rewrite = cuRewrite.getASTRewrite();
		ICompilationUnit unit = cuRewrite.getCu();
		CodeGenerationSettings settings = PreferenceManager.getCodeGenerationSettings(unit.getJavaProject().getProject());

		ListRewrite listRewrite;
		ITypeBinding currentType = null;

		if (fTypeNode instanceof AnonymousClassDeclaration) {
			AnonymousClassDeclaration decl = (AnonymousClassDeclaration) fTypeNode;
			listRewrite = rewrite.getListRewrite(decl, AnonymousClassDeclaration.BODY_DECLARATIONS_PROPERTY);
			settings.createComments = false;
			currentType = decl.resolveBinding();
		} else if (fTypeNode instanceof AbstractTypeDeclaration) {
			AbstractTypeDeclaration decl = (AbstractTypeDeclaration) fTypeNode;
			listRewrite = rewrite.getListRewrite(decl, decl.getBodyDeclarationsProperty());
			currentType = decl.resolveBinding();
		} else if (fTypeNode instanceof EnumConstantDeclaration) {
			EnumConstantDeclaration enumConstantDeclaration = (EnumConstantDeclaration) fTypeNode;
			AnonymousClassDeclaration anonymousClassDeclaration = enumConstantDeclaration.getAnonymousClassDeclaration();
			if (anonymousClassDeclaration == null) {
				anonymousClassDeclaration = rewrite.getAST().newAnonymousClassDeclaration();
				rewrite.set(enumConstantDeclaration, EnumConstantDeclaration.ANONYMOUS_CLASS_DECLARATION_PROPERTY, anonymousClassDeclaration,
						createTextEditGroup(CorrectionMessages.AddUnimplementedMethodsOperation_AddMissingMethod_group, cuRewrite));
			}
			listRewrite = rewrite.getListRewrite(anonymousClassDeclaration, AnonymousClassDeclaration.BODY_DECLARATIONS_PROPERTY);
			settings.createComments = false;
		} else {
			Assert.isTrue(false, "Unknown type node"); //$NON-NLS-1$
			return;
		}

		ImportRewrite imports = cuRewrite.getImportRewrite();

		for (int i = 0; i < unimplementedMethods.length; i++) {
			IMethodBinding curr = unimplementedMethods[i];
			MethodDeclaration newMethodDecl = StubUtility2Core.createImplementationStubCore(unit, rewrite, imports, context, curr, curr.getDeclaringClass(), settings, false, fTypeNode, false);
			listRewrite.insertLast(newMethodDecl, createTextEditGroup(CorrectionMessages.AddUnimplementedMethodsOperation_AddMissingMethod_group, cuRewrite));
		}
	}

	@Override
	public String getAdditionalInfo() {
		if (fTypeNode instanceof EnumDeclaration) {
			return CorrectionMessages.UnimplementedMethodsCorrectionProposal_enum_info;
		}

		IMethodBinding[] methodsToOverride = getMethodsToImplement();
		StringBuffer buf = new StringBuffer();
		buf.append("<b>"); //$NON-NLS-1$
		if (methodsToOverride.length == 1) {
			buf.append(CorrectionMessages.UnimplementedMethodsCorrectionProposal_info_singular);
		} else {
			buf.append(Messages.format(CorrectionMessages.UnimplementedMethodsCorrectionProposal_info_plural, String.valueOf(methodsToOverride.length)));
		}
		buf.append("</b><ul>"); //$NON-NLS-1$
		for (int i = 0; i < methodsToOverride.length; i++) {
			buf.append("<li>"); //$NON-NLS-1$
			buf.append(BindingLabelProvider.getBindingLabel(methodsToOverride[i], JavaElementLabels.ALL_FULLY_QUALIFIED));
			buf.append("</li>"); //$NON-NLS-1$s
		}
		buf.append("</ul>"); //$NON-NLS-1$
		return buf.toString();
	}

	public IMethodBinding[] getMethodsToImplement() {
		return getUnimplementedMethods(fTypeNode);
	}

	private IMethodBinding[] getUnimplementedMethods(ASTNode typeNode) {
		ITypeBinding binding = null;
		boolean implementAbstractsOfInput = false;
		if (typeNode instanceof AnonymousClassDeclaration) {
			AnonymousClassDeclaration decl = (AnonymousClassDeclaration) typeNode;
			binding = decl.resolveBinding();
		} else if (typeNode instanceof AbstractTypeDeclaration) {
			AbstractTypeDeclaration decl = (AbstractTypeDeclaration) typeNode;
			binding = decl.resolveBinding();
		} else if (typeNode instanceof EnumConstantDeclaration) {
			EnumConstantDeclaration enumConstantDeclaration = (EnumConstantDeclaration) typeNode;
			if (enumConstantDeclaration.getAnonymousClassDeclaration() != null) {
				binding = enumConstantDeclaration.getAnonymousClassDeclaration().resolveBinding();
			} else {
				IVariableBinding varBinding = enumConstantDeclaration.resolveVariable();
				if (varBinding != null) {
					binding = varBinding.getDeclaringClass();
					implementAbstractsOfInput = true;
				}
			}
		}
		if (binding == null) {
			return new IMethodBinding[0];
		}

		IMethodBinding[] unimplementedMethods = StubUtility2Core.getUnimplementedMethods(binding, implementAbstractsOfInput);
		Arrays.sort(unimplementedMethods, new MethodsSourcePositionComparator(binding));
		return unimplementedMethods;
	}
}
