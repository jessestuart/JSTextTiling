package com.jdstuart.texttiling

import com.jdstuart.texttiling.struct.Text

/**
 *
 * @author Jesse Stuart
 */
class TextTiling {
    Text text

    TextTiling(String text) {
        this.text = new Text(text).analyze()
    }

    static void main(String[] args) {
        def f = new File('src/test/resources/sample.txt')
        def tt = new TextTiling(f.text)
//        new TextTiling(new File('src/test/resources/sample2.txt').text)
    }
}
