package com.example

import com.example.kwgt.formula.KwgtEvaluator
import com.example.kwgt.model.*
import com.example.kwgt.parser.KwgtParser
import org.junit.Assert.*
import org.junit.Test

class KwgtEngineTest {

    @Test
    fun testParsePresetJson() {
        val json = """
        {
          "preset_info": {
            "title": "Test Clock",
            "author": "Tester"
          },
          "preset": {
            "root": {
              "config": {
                "width": 720,
                "height": 360
              },
              "viewgroup_items": [
                {
                  "internal_type": "ShapeModule",
                  "internal_title": "Card",
                  "config": {
                    "shape_width": 600,
                    "shape_height": 200,
                    "paint_color": -1
                  }
                },
                {
                  "internal_type": "TextModule",
                  "internal_title": "Clock",
                  "config": {
                    "text_expression": "${'$'}df(hh:mm)$",
                    "text_size": 48,
                    "paint_color": -16777216
                  }
                }
              ]
            }
          }
        }
        """.trimIndent()

        val preset = KwgtParser.parseString(json)
        assertEquals("Test Clock", preset.title)
        assertEquals(720, preset.width)
        assertEquals(360, preset.height)
        assertEquals(2, preset.rootElements.size)

        val shape = preset.rootElements[0]
        assertEquals(KwgtElementType.SHAPE, shape.type)
        assertEquals(600f, shape.width, 0.1f)
        assertEquals(200f, shape.height, 0.1f)
        assertEquals(0xFFFFFFFFL, shape.color)

        val text = preset.rootElements[1]
        assertEquals(KwgtElementType.TEXT, text.type)
        assertEquals("${'$'}df(hh:mm)$", text.textRaw)
        assertEquals(48f, text.fontSize, 0.1f)
        assertEquals(0xFF000000L, text.color)
    }

    @Test
    fun testEvaluator() {
        val formula = "Time: ${'$'}df(HH:mm)$ - ${'$'}tc(up, hello)$"
        val evaluated = KwgtEvaluator.evaluate(formula, null)
        assertTrue(evaluated.contains("Time: "))
        assertTrue(evaluated.contains("HELLO"))
    }
}
