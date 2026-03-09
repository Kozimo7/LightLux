
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

fun main(args: Array<String>) {
    val inputPath = "C:\\Users\\Cosmin\\.gemini\\antigravity\\brain\\d900f03b-9cc3-410f-8d4c-0dfc9335c1a6\\lightlux_app_icon_1772917656495.png"
    val baseOutputDir = "d:\\Projects\\LightLuxMeter\\app\\src\\main\\res"

    val sourceImage: BufferedImage = ImageIO.read(File(inputPath))

    val sizes = mapOf(
        "mdpi" to 48,
        "hdpi" to 72,
        "xhdpi" to 96,
        "xxhdpi" to 144,
        "xxxhdpi" to 192
    )

    for ((density, size) in sizes) {
        val scaledImage = sourceImage.getScaledInstance(size, size, Image.SCALE_SMOOTH)
        val bufferedScaled = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        
        val g2d = bufferedScaled.createGraphics()
        g2d.drawImage(scaledImage, 0, 0, null)
        g2d.dispose()

        // Create directories if they don't exist
        val dir = File("$baseOutputDir\\mipmap-$density")
        if (!dir.exists()) {
            dir.mkdirs()
        }

        val outputFile = File(dir, "ic_launcher_round.webp")
        // Use PNG for simplicity, Android accepts PNG as well, we'll override the webp or replace it.
        // Or write it as png and change the name. Let's write PNGs for standard Android icons.
        val pngOutputFile = File(dir, "ic_launcher_round.png")
        ImageIO.write(bufferedScaled, "png", pngOutputFile)
        
        val pngSquareFile = File(dir, "ic_launcher.png")
        ImageIO.write(bufferedScaled, "png", pngSquareFile)
        
        // Also delete old webp files so android studio picks up the new png ones
        File(dir, "ic_launcher_round.webp").delete()
        File(dir, "ic_launcher.webp").delete()

        println("Saved $pngOutputFile")
    }

    println("Done.")
}
