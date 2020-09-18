/*
 * Copyright 2020 Google LLC
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
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


package com.google.devtools.ksp.symbol.impl.kotlin

import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import com.google.devtools.ksp.isOpen
import com.google.devtools.ksp.isVisibleFrom
import com.google.devtools.ksp.processing.impl.ResolverImpl
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.symbol.impl.*
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.OverridingUtil
import org.jetbrains.kotlin.resolve.calls.inference.returnTypeOrNothing
import java.lang.IllegalStateException

class KSFunctionDeclarationImpl private constructor(val ktFunction: KtFunction) : KSFunctionDeclaration, KSDeclarationImpl(ktFunction),
    KSExpectActual by KSExpectActualImpl(ktFunction) {
    companion object : KSObjectCache<KtFunction, KSFunctionDeclarationImpl>() {
        fun getCached(ktFunction: KtFunction) = cache.getOrPut(ktFunction) { KSFunctionDeclarationImpl(ktFunction) }
    }

    override fun findOverridee(): KSFunctionDeclaration? {
        return ResolverImpl.instance.resolveFunctionDeclaration(this)?.original?.overriddenDescriptors?.single { it.overriddenDescriptors.isEmpty() }
            ?.toKSFunctionDeclaration()
    }

    override fun overrides(overridee: KSFunctionDeclaration): Boolean {
        if (!this.modifiers.contains(Modifier.OVERRIDE))
            return false
        if (!overridee.isOpen())
            return false
        if (!overridee.isVisibleFrom(this))
            return false
        val superDescriptor = ResolverImpl.instance.resolveFunctionDeclaration(overridee) ?: return false
        val subDescriptor = ResolverImpl.instance.resolveDeclaration(ktFunction) as FunctionDescriptor
        return OverridingUtil.DEFAULT.isOverridableBy(
            superDescriptor, subDescriptor, null
        ).result == OverridingUtil.OverrideCompatibilityInfo.Result.OVERRIDABLE
    }

    override val declarations: List<KSDeclaration> by lazy {
        if (!ktFunction.hasBlockBody()) {
            emptyList()
        } else {
            ktFunction.bodyBlockExpression?.statements?.getKSDeclarations() ?: emptyList()
        }
    }

    override val extensionReceiver: KSTypeReference? by lazy {
        if (ktFunction.receiverTypeReference != null) {
            KSTypeReferenceImpl.getCached(ktFunction.receiverTypeReference!!)
        } else {
            null
        }
    }

    override val functionKind: FunctionKind by lazy {
        if (parentDeclaration == null) {
            FunctionKind.TOP_LEVEL
        } else {
            when (ktFunction) {
                is KtNamedFunction, is KtPrimaryConstructor, is KtSecondaryConstructor -> FunctionKind.MEMBER
                is KtFunctionLiteral -> if (ktFunction.node.findChildByType(KtTokens.FUN_KEYWORD) != null) FunctionKind.ANONYMOUS else FunctionKind.LAMBDA
                else -> throw IllegalStateException()
            }
        }
    }

    override val isAbstract: Boolean by lazy {
        this.modifiers.contains(Modifier.ABSTRACT) ||
                ((this.parentDeclaration as? KSClassDeclaration)?.classKind == ClassKind.INTERFACE
                        && !this.ktFunction.hasBody())
    }

    override val parameters: List<KSVariableParameter> by lazy {
        ktFunction.valueParameters.map { KSVariableParameterImpl.getCached(it) }
    }

    override val returnType: KSTypeReference by lazy {
        if (ktFunction.typeReference != null) {
            KSTypeReferenceImpl.getCached(ktFunction.typeReference!!)
        } else {
            KSTypeReferenceDeferredImpl.getCached {
                val desc = ResolverImpl.instance.resolveDeclaration(ktFunction) as FunctionDescriptor
                getKSTypeCached(desc.returnTypeOrNothing)
            }
        }
    }

    override fun <D, R> accept(visitor: KSVisitor<D, R>, data: D): R {
        return visitor.visitFunctionDeclaration(this, data)
    }
}

