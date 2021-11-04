@file:Suppress("UnstableApiUsage")

package io.sentry.android.gradle.instrumentation.androidx.room

import com.android.build.api.instrumentation.ClassContext
import io.sentry.android.gradle.instrumentation.ClassInstrumentable
import io.sentry.android.gradle.instrumentation.CommonClassVisitor
import io.sentry.android.gradle.instrumentation.MethodContext
import io.sentry.android.gradle.instrumentation.MethodInstrumentable
import io.sentry.android.gradle.instrumentation.SpanAddingClassVisitorFactory
import io.sentry.android.gradle.instrumentation.androidx.room.visitor.InstrumentableMethodsCollectingVisitor
import io.sentry.android.gradle.instrumentation.androidx.room.visitor.RoomQueryVisitor
import io.sentry.android.gradle.instrumentation.androidx.room.visitor.RoomQueryWithTransactionVisitor
import io.sentry.android.gradle.instrumentation.androidx.room.visitor.RoomTransactionVisitor
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.MethodNode
import org.slf4j.LoggerFactory

class AndroidXRoomDao : ClassInstrumentable {

    override val fqName: String get() = "androidx.room.Dao"
    private val logger by lazy { LoggerFactory.getLogger(this::class.java) }

    override fun getVisitor(
        instrumentableContext: ClassContext,
        apiVersion: Int,
        originalVisitor: ClassVisitor,
        parameters: SpanAddingClassVisitorFactory.SpanAddingParameters
    ): ClassVisitor {
        val currentClassName = instrumentableContext.currentClassData.className
        val originalClassName = currentClassName.substringBefore(IMPL_SUFFIX)
        val originalClass = instrumentableContext.loadClassData(originalClassName)
        return if (originalClass != null && fqName in originalClass.classAnnotations) {
            InstrumentableMethodsCollectingVisitor(
                apiVersion,
                nextVisitorInitializer = { methodsToInstrument ->
                    if (methodsToInstrument.isEmpty()) {
                        originalVisitor
                    } else {
                        CommonClassVisitor(
                            apiVersion = apiVersion,
                            classVisitor = originalVisitor,
                            className = currentClassName.substringAfterLast('.'),
                            methodInstrumentables = methodsToInstrument.map { (methodNode, type) ->
                                RoomMethod(methodNode, type)
                            },
                            parameters = parameters
                        )
                    }
                }
            )
        } else {
            if (parameters.debug.get() && originalClass == null) {
                logger.info("Expected $originalClassName in the classpath, but failed to discover")
            }
            originalVisitor
        }
    }

    override fun isInstrumentable(data: ClassContext): Boolean =
        // this is not very deterministic, but we filter out false positives in [getVisitor]
        // based on the androidx.room.Dao annotation
        IMPL_SUFFIX in data.currentClassData.className

    companion object {
        private const val IMPL_SUFFIX = "_Impl"
    }
}

class RoomMethod(
    private val methodNode: MethodNode,
    private val type: RoomMethodType
) : MethodInstrumentable {

    override val fqName: String = methodNode.name

    override fun getVisitor(
        instrumentableContext: MethodContext,
        apiVersion: Int,
        originalVisitor: MethodVisitor,
        parameters: SpanAddingClassVisitorFactory.SpanAddingParameters
    ): MethodVisitor = when (type) {
        RoomMethodType.TRANSACTION -> RoomTransactionVisitor(
            apiVersion,
            methodNode,
            originalVisitor,
            instrumentableContext.access,
            instrumentableContext.descriptor
        )
        RoomMethodType.QUERY -> RoomQueryVisitor(
            apiVersion,
            methodNode,
            originalVisitor,
            instrumentableContext.access,
            instrumentableContext.descriptor
        )
        RoomMethodType.QUERY_WITH_TRANSACTION -> RoomQueryWithTransactionVisitor(
            apiVersion,
            methodNode,
            originalVisitor,
            instrumentableContext.access,
            instrumentableContext.descriptor
        )
    }

    override fun isInstrumentable(data: MethodContext): Boolean {
        return data.name == fqName && data.descriptor == methodNode.desc &&
            // we only care about public Dao methods
            (data.access and Opcodes.ACC_PUBLIC) != 0 &&
            // there's a synthetic bridge method generated by java compiler with the same name which we don't want to instrument
            (data.access and Opcodes.ACC_BRIDGE) == 0
    }
}
