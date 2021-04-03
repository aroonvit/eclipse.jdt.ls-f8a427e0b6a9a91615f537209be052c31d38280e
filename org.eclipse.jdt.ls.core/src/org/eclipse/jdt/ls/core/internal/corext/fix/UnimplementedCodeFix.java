/*******************************************************************************
 * Copyright (c) 2007, 2016 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Copied from org.eclipse.jdt.internal.corext.fix.UnimplementedCodeFix
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.corext.fix;

import java.util.ArrayList;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnumConstantDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.internal.core.manipulation.util.BasicElementLabels;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore;
import org.eclipse.jdt.internal.corext.fix.ICleanUpFixCore;
import org.eclipse.jdt.internal.corext.fix.IProposableFix;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalModelCore;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalPositionGroupCore;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.jdt.internal.ui.text.correction.IProblemLocationCore;
import org.eclipse.jdt.ls.core.internal.Messages;
import org.eclipse.jdt.ls.core.internal.corrections.CorrectionMessages;
import org.eclipse.text.edits.TextEditGroup;


public class UnimplementedCodeFix extends CompilationUnitRewriteOperationsFixCore {

	public static final class MakeTypeAbstractOperation extends CompilationUnitRewriteOperation {

		private final TypeDeclaration fTypeDeclaration;

		public MakeTypeAbstractOperation(TypeDeclaration typeDeclaration) {
			fTypeDeclaration= typeDeclaration;
		}

		@Override
		public void rewriteAST(CompilationUnitRewrite cuRewrite, LinkedProposalModelCore linkedProposalPositions) throws CoreException {
			AST ast= cuRewrite.getAST();
			ASTRewrite rewrite= cuRewrite.getASTRewrite();
			Modifier newModifier= ast.newModifier(Modifier.ModifierKeyword.ABSTRACT_KEYWORD);
			TextEditGroup textEditGroup= createTextEditGroup(CorrectionMessages.UnimplementedCodeFix_TextEditGroup_label, cuRewrite);
			rewrite.getListRewrite(fTypeDeclaration, TypeDeclaration.MODIFIERS2_PROPERTY).insertLast(newModifier, textEditGroup);

			LinkedProposalPositionGroupCore group = new LinkedProposalPositionGroupCore("modifier"); //$NON-NLS-1$
			group.addPosition(rewrite.track(newModifier), !linkedProposalPositions.hasLinkedPositions());
			linkedProposalPositions.addPositionGroup(group);
		}
	}

	public static ICleanUpFixCore createCleanUp(CompilationUnit root, boolean addMissingMethod, boolean makeTypeAbstract, IProblemLocationCore[] problems) {
		Assert.isLegal(!addMissingMethod || !makeTypeAbstract);
		if (!addMissingMethod && !makeTypeAbstract) {
			return null;
		}

		if (problems.length == 0) {
			return null;
		}

		ArrayList<CompilationUnitRewriteOperation> operations= new ArrayList<>();

		for (int i= 0; i < problems.length; i++) {
			IProblemLocationCore problem = problems[i];
			if (addMissingMethod) {
				ASTNode typeNode= getSelectedTypeNode(root, problem);
				if (typeNode != null && !isTypeBindingNull(typeNode)) {
					operations.add(new AddUnimplementedMethodsOperation(typeNode));
				}
			} else {
				ASTNode typeNode= getSelectedTypeNode(root, problem);
				if (typeNode instanceof TypeDeclaration) {
					operations.add(new MakeTypeAbstractOperation((TypeDeclaration) typeNode));
				}
			}
		}

		if (operations.size() == 0) {
			return null;
		}

		String label;
		if (addMissingMethod) {
			label= CorrectionMessages.UnimplementedMethodsCorrectionProposal_description;
		} else {
			label= CorrectionMessages.UnimplementedCodeFix_MakeAbstractFix_label;
		}
		return new UnimplementedCodeFix(label, root, operations.toArray(new CompilationUnitRewriteOperation[operations.size()]));
	}

	public static IProposableFix createAddUnimplementedMethodsFix(final CompilationUnit root, IProblemLocationCore problem) {
		ASTNode typeNode= getSelectedTypeNode(root, problem);
		if (typeNode == null) {
			return null;
		}

		if (isTypeBindingNull(typeNode)) {
			return null;
		}

		AddUnimplementedMethodsOperation operation= new AddUnimplementedMethodsOperation(typeNode);
		if (operation.getMethodsToImplement().length > 0) {
			return new UnimplementedCodeFix(CorrectionMessages.UnimplementedMethodsCorrectionProposal_description, root, new CompilationUnitRewriteOperation[] { operation });
		}
		return null;
		//			else {
//			return new IProposableFix() {
//				@Override
//				public CompilationUnitChange createChange(IProgressMonitor progressMonitor) throws CoreException {
//					CompilationUnitChange change= new CompilationUnitChange(CorrectionMessages.UnimplementedMethodsCorrectionProposal_description, (ICompilationUnit) root.getJavaElement()) {
//						@Override
//						public Change perform(IProgressMonitor pm) throws CoreException {
//							Shell shell= PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
//							String dialogTitle= CorrectionMessages.UnimplementedMethodsCorrectionProposal_description;
//							IStatus status= getStatus();
//							ErrorDialog.openError(shell, dialogTitle, CorrectionMessages.UnimplementedCodeFix_DependenciesErrorMessage, status);
//
//							return new NullChange();
//						}
//					};
//					change.setEdit(new MultiTextEdit());
//					return change;
//				}
//
//				@Override
//				public String getAdditionalProposalInfo() {
//					return new String();
//				}
//
//				@Override
//				public String getDisplayString() {
//					return CorrectionMessages.UnimplementedMethodsCorrectionProposal_description;
//				}
//
//				@Override
//				public IStatus getStatus() {
//					return new Status(IStatus.ERROR, JavaUI.ID_PLUGIN, CorrectionMessages.UnimplementedCodeFix_DependenciesStatusMessage);
//				}
//			};
//		}
	}

	public static UnimplementedCodeFix createMakeTypeAbstractFix(CompilationUnit root, IProblemLocationCore problem) {
		ASTNode typeNode= getSelectedTypeNode(root, problem);
		if (!(typeNode instanceof TypeDeclaration)) {
			return null;
		}

		TypeDeclaration typeDeclaration= (TypeDeclaration) typeNode;
		MakeTypeAbstractOperation operation= new MakeTypeAbstractOperation(typeDeclaration);

		String label= Messages.format(CorrectionMessages.ModifierCorrectionSubProcessor_addabstract_description, BasicElementLabels.getJavaElementName(typeDeclaration.getName().getIdentifier()));
		return new UnimplementedCodeFix(label, root, new CompilationUnitRewriteOperation[] { operation });
	}

	public static ASTNode getSelectedTypeNode(CompilationUnit root, IProblemLocationCore problem) {
		ASTNode selectedNode= problem.getCoveringNode(root);
		if (selectedNode == null) {
			return null;
		}

		if (selectedNode.getNodeType() == ASTNode.ANONYMOUS_CLASS_DECLARATION) { // bug 200016
			selectedNode= selectedNode.getParent();
		}

		if (selectedNode.getLocationInParent() == EnumConstantDeclaration.NAME_PROPERTY) {
			selectedNode= selectedNode.getParent();
		}
		if (selectedNode.getNodeType() == ASTNode.SIMPLE_NAME && selectedNode.getParent() instanceof AbstractTypeDeclaration) {
			return selectedNode.getParent();
		} else if (selectedNode.getNodeType() == ASTNode.CLASS_INSTANCE_CREATION) {
			return ((ClassInstanceCreation) selectedNode).getAnonymousClassDeclaration();
		} else if (selectedNode.getNodeType() == ASTNode.ENUM_CONSTANT_DECLARATION) {
			EnumConstantDeclaration enumConst= (EnumConstantDeclaration) selectedNode;
			if (enumConst.getAnonymousClassDeclaration() != null) {
				return enumConst.getAnonymousClassDeclaration();
			}
			return enumConst;
		} else {
			return null;
		}
	}

	private static boolean isTypeBindingNull(ASTNode typeNode) {
		if (typeNode instanceof AbstractTypeDeclaration) {
			AbstractTypeDeclaration abstractTypeDeclaration= (AbstractTypeDeclaration) typeNode;
			if (abstractTypeDeclaration.resolveBinding() == null) {
				return true;
			}

			return false;
		} else if (typeNode instanceof AnonymousClassDeclaration) {
			AnonymousClassDeclaration anonymousClassDeclaration= (AnonymousClassDeclaration) typeNode;
			if (anonymousClassDeclaration.resolveBinding() == null) {
				return true;
			}

			return false;
		} else if (typeNode instanceof EnumConstantDeclaration) {
			return false;
		} else {
			return true;
		}
	}

	public UnimplementedCodeFix(String name, CompilationUnit compilationUnit, CompilationUnitRewriteOperation[] fixRewriteOperations) {
		super(name, compilationUnit, fixRewriteOperations);
	}
}
