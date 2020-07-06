package net.mamoe.kjbb.ir

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.ir.copyTo
import org.jetbrains.kotlin.backend.common.ir.copyTypeParametersFrom
import org.jetbrains.kotlin.backend.common.ir.copyValueParametersFrom
import org.jetbrains.kotlin.backend.common.ir.createImplicitParameterDeclarationWithWrappedDescriptor
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.builders.declarations.addConstructor
import org.jetbrains.kotlin.ir.builders.declarations.addFunction
import org.jetbrains.kotlin.ir.builders.declarations.buildClass
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrContainerExpression
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.ir.util.primaryConstructor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

val JVM_BLOCKING_BRIDGE_FQ_NAME = FqName("net.mamoe.kjbb.JvmBlockingBridge")

object KOTLINX_COROUTINES {
    private val pkg = FqName("kotlinx.coroutines")
    val RUN_BLOCKING = pkg.child(Name.identifier("runBlocking"))
    val CoroutineScope = pkg.child(Name.identifier("CoroutineScope"))
}

val IrFunction.bridgeFunctionName: Name get() = Name.identifier("${this.name}") // TODO: 2020/7/3

val ORIGIN_JVM_BLOCKING_BRIDGE: IrDeclarationOrigin get() = Origin_JVM_BLOCKING_BRIDGE

private fun IrPluginContext.referenceFunctionRunBlocking(): IrSimpleFunctionSymbol {
    return referenceFunctions(KOTLINX_COROUTINES.RUN_BLOCKING).singleOrNull()
        ?: error("kotlinx.coroutines.runBlocking not found.")
}

private fun IrPluginContext.referenceCoroutineScope(): IrClassSymbol {
    return referenceClass(KOTLINX_COROUTINES.CoroutineScope)
        ?: error("kotlinx.coroutines.CoroutineScope not found.")
}

@Suppress("ClassName")
private object Origin_JVM_BLOCKING_BRIDGE : IrDeclarationOriginImpl("JVM_BLOCKING_BRIDGE", isSynthetic = true)

fun IrPluginContext.lowerOriginFunction(originFunction: IrFunction): List<IrDeclaration>? {
    println("lowering function ${originFunction.name}")
    val originClass = originFunction.parentAsClass

    val perquisite = mutableListOf<IrDeclaration>()

    val bridgeFunction = buildFun {
        updateFrom(originFunction)
        origin = ORIGIN_JVM_BLOCKING_BRIDGE

        name = originFunction.bridgeFunctionName
        modality = Modality.OPEN
        returnType = originFunction.returnType

        isExternal = false
        isInline = false
        isOperator = false
        // TODO: 2020/7/5 handle EXPECT
        isSuspend = false
    }.apply fn@{
        this.parent = originClass
        this.extensionReceiverParameter = originFunction.extensionReceiverParameter?.copyTo(this@fn)
        this.dispatchReceiverParameter = originFunction.dispatchReceiverParameter?.copyTo(this@fn)

        copyTypeParametersFrom(originFunction)
        copyValueParametersFrom(originFunction)
        this.body = createIrBuilder(symbol).irBlockBody {

            // public fun <T> runBlocking(context: CoroutineContext = EmptyCoroutineContext, block: suspend CoroutineScope.() -> T): T

            val constructor: IrConstructor


            /*
            // class for lambda `suspend CoroutineScope.() -> T`
            val lambdaClass = buildClass {
                this.visibility = Visibilities.PUBLIC
                this.name =
                    Name.identifier(originClass.name.identifier + "\$" + originFunction.name.identifier + "\$\$JBB")
            }.apply lambdaClass@{
                this.createImplicitParameterDeclarationWithWrappedDescriptor()

                this.parent = originClass
                this.thisReceiver = buildValueParameter {
                    this.name = Name.identifier("this")
                    this.type = IrSimpleTypeImpl(this@lambdaClass.symbol, false, listOf(), listOf(), null)
                }

                constructor = addConstructor {
                    isPrimary = true
                }

                /*
                // fun invoke()
                (this as IrDeclarationContainer).addFunction {
                    this.name = Name.identifier("invoke")
                    addDispatchReceiver { type = this@lambdaClass.defaultType }
                }.apply {
                    this.parent = this@lambdaClass
                    this.body = irBlockBody {
                        // call original function
                        +irCall(originFunction).apply {
                            originFunction.typeParameters.forEachIndexed { index, irTypeParameter ->
                                putTypeArgument(index, this@fn.typeParameters[index].defaultType)
                            }
                            originFunction.valueParameters.forEachIndexed { index, irTypeParameter ->
                                putValueArgument(index, irGet(this@fn.valueParameters[index]))
                            }
                        }
                    }
                }*/
            }
*/
            //    loweringResult.add(lambdaClass)

            // given: suspend fun <T, R, ...> T.test(params): R
            // gen:           fun <T, R, ...> T.test(params): R

            val runBlockingFun = referenceFunctionRunBlocking()
            +irReturn(
                // call `kotlinx.coroutines.runBlocking<R>(CoroutineContext = ..., suspend CoroutineScope.() -> R): R`
                irCall(runBlockingFun).apply {
                    putTypeArgument(0, this@fn.returnType) // the R for runBlocking

                    // take default value for value argument 0

                    val suspendLambda = createSuspendLambda(
                        parent = this@fn,
                        objectName = "${originClass.name}\$\$${originFunction.name}\$blocking_bridge",
                        lambdaType = symbols.suspendFunctionN(1).typeWith(referenceCoroutineScope().defaultType),
                        returnType = this@fn.returnType
                    ) {
                        this.returnType = this@fn.returnType
                        this.body = irBlockBody {
                            +irCall(originFunction).apply {
                                originClass.thisReceiver?.let { receiver ->
                                    this.dispatchReceiver = irGet(receiver)
                                }
                                originFunction.typeParameters.forEachIndexed { index, _ ->
                                    putTypeArgument(index, this@fn.typeParameters[index].defaultType)
                                }
                                originFunction.valueParameters.forEachIndexed { index, _ ->
                                    putValueArgument(index, irGet(this@fn.valueParameters[index]))
                                }
                            }
                        }
                    }.also {
                        +it
                    }
                    putValueArgument(1, irCall(suspendLambda.primaryConstructor!!))
                }
            )
            /*

            +irReturn(irCall(originFunction).apply {
                for (param in valueParameters) {
                    addArguments(valueParameters.associateWith { irGet(it) }.mapKeys { it.key.descriptor })
                }
            })

            */
        }
    }

    return listOf(bridgeFunction) + perquisite
}

/**
 * Generate an anonymous object extending `suspend CoroutineScope.() -> Unit`
 */
fun IrPluginContext.createSuspendLambda(
    parent: IrDeclarationParent,
    objectName: String,
    lambdaType: IrType,
    returnType: IrType,
    body: IrSimpleFunction.() -> Unit
): IrClass {
    /*
    val s: suspend CoroutineScope.() -> R = {

    }
     */

    return buildClass {
        name = Name.identifier(objectName)
        kind = ClassKind.CLASS
        visibility = Visibilities.PUBLIC
    }.apply clazz@{
        this.parent = parent
        superTypes = listOf(lambdaType)

        createImplicitParameterDeclarationWithWrappedDescriptor()
        addConstructor {
            isPrimary = true
        }.apply {
            this.body = createIrBuilder(this.symbol).irBlockBody {
                +irDelegatingConstructorCall(context.irBuiltIns.anyClass.owner.constructors.single())
            }
        }
        addFunction("invoke", returnType, isSuspend = true).apply {
            body()
        }
    }
}

fun IrPluginContext.createIrBuilder(
    symbol: IrSymbol,
    startOffset: Int = UNDEFINED_OFFSET,
    endOffset: Int = UNDEFINED_OFFSET
) = DeclarationIrBuilder(this, symbol, startOffset, endOffset)


inline fun IrBuilderWithScope.irBlock(
    startOffset: Int = this.startOffset,
    endOffset: Int = this.endOffset,
    origin: IrStatementOrigin? = null,
    resultType: IrType? = null,
    isTransparent: Boolean = false,
    body: IrBlockBuilder.() -> Unit
): IrContainerExpression =
    IrBlockBuilder(
        context, scope,
        startOffset,
        endOffset,
        origin, resultType, isTransparent
    ).block(body)

inline fun IrBuilderWithScope.irBlockBody(
    startOffset: Int = this.startOffset,
    endOffset: Int = this.endOffset,
    body: IrBlockBodyBuilder.() -> Unit
): IrBlockBody =
    IrBlockBodyBuilder(
        context, scope,
        startOffset,
        endOffset
    ).blockBody(body)
