package com.example.tfgy999

import android.opengl.GLES20
import java.util.concurrent.Executors

class TileRenderer(private val service: AutoFrameBoostService) {
    private val tileExecutor = Executors.newFixedThreadPool(8)

    fun renderTilesAsync(width: Int, height: Int) {
        val tiles = splitScreenToTiles(width, height, 8)
        tiles.forEach { tile ->
            tileExecutor.submit {
                renderTile(tile)
            }
        }
    }

    private fun splitScreenToTiles(width: Int, height: Int, tileCount: Int): List<Tile> {
        val tiles = mutableListOf<Tile>()
        val tileWidth = width / tileCount
        val tileHeight = height / tileCount
        for (y in 0 until tileCount) {
            for (x in 0 until tileCount) {
                tiles.add(Tile(x * tileWidth, y * tileHeight, tileWidth, tileHeight))
            }
        }
        return tiles
    }

    private fun renderTile(tile: Tile) {
        synchronized(service) {
            if (!service.makeCurrent()) return
            GLES20.glViewport(tile.x, tile.y, tile.width, tile.height)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, service.getRenderTextureId())
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        }
    }

    data class Tile(val x: Int, val y: Int, val width: Int, val height: Int)
}
