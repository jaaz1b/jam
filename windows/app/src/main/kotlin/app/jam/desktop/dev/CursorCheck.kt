package app.jam.desktop.dev

/** Development: Jam's pointers as pictures, to look at them. */
fun main() {
    val out = java.awt.image.BufferedImage(200, 100, java.awt.image.BufferedImage.TYPE_INT_ARGB)
    val g = out.createGraphics()
    g.color = java.awt.Color(0x18, 0x18, 0x20); g.fillRect(0, 0, 200, 100)
    g.drawImage(app.jam.desktop.studio.Cursors.image(0xFF8B5CF6.toInt(), false, 64), 10, 10, null)
    g.drawImage(app.jam.desktop.studio.Cursors.image(0xFF8B5CF6.toInt(), true, 64), 100, 10, null)
    g.dispose()
    javax.imageio.ImageIO.write(out, "png", java.io.File("/tmp/cursors.png"))
}
