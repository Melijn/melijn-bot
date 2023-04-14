package me.melijn.bot.utils.image

import java.awt.image.BufferedImage
import java.io.Closeable
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageTypeSpecifier
import javax.imageio.metadata.IIOMetadata
import javax.imageio.metadata.IIOMetadataNode
import javax.imageio.stream.ImageOutputStream


class GifSequenceWriter(outputStream: ImageOutputStream, iterations: Int) : Closeable {

    private val writer = ImageIO.getImageWritersBySuffix("gif").next()
    private val params = writer.defaultWriteParam
    var frameType: Int = 0
    private val metadata by lazy { configureRootMetadata(iterations) }

    init {
        writer.output = outputStream
        writer.prepareWriteSequence(null)
    }

    private fun configureRootMetadata(iterations: Int): IIOMetadata {
        val imageTypeSpecifier: ImageTypeSpecifier = ImageTypeSpecifier.createFromBufferedImageType(frameType)
        val metadata = writer.getDefaultImageMetadata(imageTypeSpecifier, params)
        val metaFormatName: String = metadata.nativeMetadataFormatName
        val root: IIOMetadataNode = metadata.getAsTree(metaFormatName) as IIOMetadataNode

        val graphicsControlExtensionNode: IIOMetadataNode = getNode(root, "GraphicControlExtension")
        graphicsControlExtensionNode.setAttribute("disposalMethod", "restoreToBackgroundColor")
        graphicsControlExtensionNode.setAttribute("userInputFlag", "FALSE")
        graphicsControlExtensionNode.setAttribute("transparentColorFlag", "FALSE")
        graphicsControlExtensionNode.setAttribute("transparentColorIndex", "0")

        val appExtensionNode: IIOMetadataNode = getNode(root, "ApplicationExtensions")
        val child = IIOMetadataNode("ApplicationExtension")
        child.setAttribute("applicationID", "NETSCAPE")
        child.setAttribute("authenticationCode", "2.0")

        child.userObject = byteArrayOf(0b1, iterations.toByte(), 0b0)
        appExtensionNode.appendChild(child)
        metadata.setFromTree(metaFormatName, root)
        return metadata
    }

    /**
     * [delayMillis] in milliseconds
     */
    fun writeToSequence(img: BufferedImage, delayMillis: Int): GifSequenceWriter {
        frameType = img.type
        val metaFormatName: String = metadata.nativeMetadataFormatName
        val root: IIOMetadataNode = metadata.getAsTree(metaFormatName) as IIOMetadataNode

        val graphicsControlExtensionNode: IIOMetadataNode = getNode(root, "GraphicControlExtension")

        graphicsControlExtensionNode.setAttribute("delayTime", "${delayMillis / 10}")
        metadata.setFromTree(metaFormatName, root)

        writer.writeToSequence(IIOImage(img, null, metadata), params)
        return this
    }

    override fun close() {
        writer.endWriteSequence()
    }

    companion object {
        private fun getNode(rootNode: IIOMetadataNode, nodeName: String): IIOMetadataNode {
            val nNodes = rootNode.length
            for (i in 0 until nNodes) {
                if (rootNode.item(i).nodeName.equals(nodeName, ignoreCase = true)) {
                    return rootNode.item(i) as IIOMetadataNode
                }
            }
            val node = IIOMetadataNode(nodeName)
            rootNode.appendChild(node)
            return node
        }
    }
}