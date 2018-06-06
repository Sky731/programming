package xyz.sky731.programming.lab7

import xyz.sky731.programming.lab3.Bredlam
import java.awt.Dimension
import java.awt.EventQueue
import java.awt.FlowLayout
import java.awt.GridLayout
import java.util.*
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JTree

class ServerGUI(name: String = "Hello", queue: Queue<Bredlam>) : JFrame(name) {


  init {
    minimumSize = Dimension(800, 400)
    defaultCloseOperation = JFrame.EXIT_ON_CLOSE
    isVisible = true

    contentPane.layout = GridLayout().apply {
      add(JButton("ClickMe").apply {
        addActionListener {
          this@ServerGUI.dispose()

        }
      })
      add(BredlamTree(queue))
      add(JLabel("ClickLeft"))
    }

    pack()

  }

}