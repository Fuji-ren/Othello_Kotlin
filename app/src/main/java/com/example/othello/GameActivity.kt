package com.example.othello

import android.graphics.Point
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.view.Gravity
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.gridlayout.widget.GridLayout

class GameActivity : AppCompatActivity() {

    val map: Array<Array<Int>> = arrayOf(
            arrayOf(0, 0, 0, 0, 0, 0, 0, 0),
            arrayOf(0, 0, 0, 0, 0, 0, 0, 0),
            arrayOf(0, 0, 0, 0, 0, 0, 0, 0),
            arrayOf(0, 0, 0, 1, 2, 0, 0, 0),
            arrayOf(0, 0, 0, 2, 1, 0, 0, 0),
            arrayOf(0, 0, 0, 0, 0, 0, 0, 0),
            arrayOf(0, 0, 0, 0, 0, 0, 0, 0),
            arrayOf(0, 0, 0, 0, 0, 0, 0, 0)
    )

    //現在の順番
    // 1:黒
    // 2:白
    var turn: Int = 1

    val imageViews: Array<Array<ImageView?>> = Array(8, {arrayOfNulls<ImageView>(8)})

    var currentPlayerTextView: TextView? = null

    var progressBar: ProgressBar? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)

        currentPlayerTextView = findViewById(R.id.textView)
        currentPlayerTextView?.setText(R.string.current_player_black)

        progressBar = findViewById(R.id.progressBar)
        progressBar?.visibility = ProgressBar.INVISIBLE

        val gridLayout: GridLayout = findViewById(R.id.gridLayout)
        for (x in 0 until 8) {
            for (y in 0 until 8) {
                val imageView = ImageView(this)
                val params = GridLayout.LayoutParams()
                params.width = 100
                params.height = 100
                params.rowSpec = GridLayout.spec(x)
                params.columnSpec = GridLayout.spec(y)
                imageView.layoutParams = params

                imageView.setOnClickListener {
                    process(Point(x, y))
                }
                imageViews[x][y] = imageView
                gridLayout.addView(imageView)
            }
        }

        display()
    }

    fun display() {
        for (x in 0 until 8) {
            for (y in 0 until 8) {
                imageViews[x][y]?.setImageResource(
                        when(map[x][y]) {
                            1    -> R.drawable.black
                            2    -> R.drawable.white
                            else -> R.drawable.none
                        }
                )
            }
        }
    }

    //プレイヤーが置いた時の処理
    fun process(put: Point) {
        if (putPoint(put, turn , map, true) > 0) {
            display()
        } else {
            showToast(message = "このマスには置けません")
            return
        }
        turn = 3 - turn
        currentPlayerTextView?.setText(if (turn == 1) R.string.current_player_black else R.string.current_player_white)
        //相手がスキップかどうか
        if (isSkip(turn)) {
            if (isSkip(3 - turn)) {
                //自分もスキップだとゲーム終了
                gameFinish()
            } else {
                showToast(message = "スキップ")
                turn = 3 - turn
                currentPlayerTextView?.setText(if (turn == 1) R.string.current_player_black else R.string.current_player_white)
                if (turn == 2) {
                    progressBar?.visibility = ProgressBar.VISIBLE
                    Handler().postDelayed(Runnable {
                        process(com(turn, map))
                        progressBar?.visibility = ProgressBar.INVISIBLE
                    }, 2000)
                } else {
                    //自分の番、自分が打つまで何もしない
                }
            }
        } else if (turn == 2) {
            progressBar?.visibility = ProgressBar.VISIBLE
            Handler().postDelayed(Runnable {
                process(com(turn, map))
                progressBar?.visibility = ProgressBar.INVISIBLE
            }, 2000)
        }
    }

    //置けるマス目のリストを返す
    fun canPutList(t: Int = turn, m: Array<Array<Int>> = map): Array<Point> {
        val list: MutableList<Point> = mutableListOf()
        for (x in 0 until 8) {
            for (y in 0 until 8) {
                if (putPoint(Point(x, y), t, m, false) > 0)
                    list.add(Point(x, y))
            }
        }
        return list.toTypedArray()
    }

    //相手の置くマスを返す
    fun com(turn: Int, map: Array<Array<Int>>): Point {
        var virtualMap: Array<Array<Int>> = getMapCopy(map)
        var maxScore = -9999
        var bestPut: Point = Point(0, 0)

        //COMが置ける全通り試す
        for (point in canPutList(turn, map)) {
            putPoint(point, turn, virtualMap, true)
            var virtualMapAfterPut = getMapCopy(virtualMap)
            var aiteBestScore = 99999
            //プレイヤーが置ける全通り試す
            val canPutList = canPutList(3 - turn, virtualMapAfterPut)
            if (canPutList.count() == 0) {
                aiteBestScore = calcMapScore(turn, virtualMapAfterPut)
            } else {
                for (point in canPutList) {
                    putPoint(point, 3 - turn, virtualMapAfterPut, true)
                    //プレイヤーが置いた後の盤面を評価して、一番低いScoreを記録 (プレイヤーは最善手を打つ)
                    var score = calcMapScore(turn, virtualMapAfterPut)
                    if (score <= aiteBestScore)
                        aiteBestScore = score
                    virtualMapAfterPut = getMapCopy(virtualMap)
                }
            }
            //今試している手に対して相手が最善の手を打ってくる前提で、最大Scoreになる手を記録
            if (aiteBestScore >= maxScore) {
                maxScore = aiteBestScore
                bestPut = point
            }
            virtualMap = getMapCopy(map)
        }
        return bestPut
    }

    fun calcMapScore(turn: Int, map: Array<Array<Int>>): Int {
        //盤面の評価 [参考](https://uguisu.skr.jp/othello/5-1.html)
        val mapRatings: Array<Array<Int>> = arrayOf(
                arrayOf( 30, -12,  0, -1, -1,  0,-12, 30),
                arrayOf(-12, -15, -3, -3, -3, -3,-15,-12),
                arrayOf(  0,  -3,  0, -1, -1,  0, -3, -1),
                arrayOf( -1,  -3, -1, -1, -1, -1, -3, -1),
                arrayOf( -1,  -3, -1, -1, -1, -1, -3, -1),
                arrayOf(  0,  -3,  0, -1, -1,  0, -3, -1),
                arrayOf(-12, -15, -3, -3, -3, -3,-15,-12),
                arrayOf( 30, -12,  0, -1, -1,  0,-12, 30)
        )

        var score = 0
        var pointCount = 0
        for (a in 0 until 8) {
            for (b in 0 until 8) {
                score += mapRatings[a][b] * when (map[a][b]) {
                    turn     -> 1
                    3 - turn -> -1
                    else     -> 0
                }
                if (map[a][b] == turn)
                    pointCount++
            }
        }
        //自分のコマ数が０の状態は負けなので低い点数を付ける
        if (pointCount <= 0)
            score = -99999
        return score
    }

    fun getMapCopy(array: Array<Array<Int>>): Array<Array<Int>> {
        val copy = Array(array.size) { Array<Int>(array[0].size) { 0 } }
        for (i in array.indices) {
            for (j in array[i].indices) {
                copy[i][j] = array[i][j]
            }
        }
        return copy
    }

    //ひっくり返す処理
    fun putPoint(put: Point, t: Int = turn, m: Array<Array<Int>> = map, exec: Boolean): Int {
        if (put.x !in 0 until 8 || put.y !in 0 until 8 ||
                m[put.x][put.y] != 0)
            return 0

        var allCount = 0

        for (dirX in -1..1) {
            for (dirY in -1..1) {
                var value: Point = Point(put.x + dirX, put.y + dirY)
                var count = 0
                while (value.x in (0 until 8) && value.y in (0 until 8) &&
                        m[value.x][value.y] == 3 - t) {
                    count++
                    value = Point(value.x + dirX, value.y + dirY)
                }
                if (count > 0 &&
                        value.x in (0 until 8) && value.y in (0 until 8) &&
                        m[value.x][value.y] == t) {
                    allCount += count
                    if (exec) {
                        while (count-- >= 0) {
                            value = Point(value.x - dirX, value.y - dirY)
                            m[value.x][value.y] = t
                        }
                    }
                }
            }
        }
        return allCount
    }

    //スキップか判定
    fun isSkip(turn: Int, m: Array<Array<Int>> = map): Boolean {
        for (x in 0 until 8) {
            for (y in 0 until 8) {
                val point = Point(x, y)
                if (putPoint(point, turn, m, false) > 0) {
                    return false
                }
            }
        }
        return true
    }

    fun showDialog(title: String = if (turn == 1) "黒" else "白", message: String) {
        AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK"){ dialog, which -> }
                .show()
    }

    fun showToast(player: String = if (turn == 1) "黒" else "白", message: String) {
        val toast = Toast.makeText(applicationContext, "${player}:${message}", Toast.LENGTH_SHORT)
        toast.setGravity(Gravity.CENTER, 0, 0)
        toast.show()
    }

    fun gameFinish(showDialog: Boolean = true): Int {
        currentPlayerTextView?.setText(R.string.game_finish)
        var blackCount = 0
        var whiteCount = 0
        for (x in 0 until 8) {
            for (y in 0 until 8) {
                if (map[x][y] == 1) blackCount++
                else if (map[x][y] == 2) whiteCount++
            }
        }
        var result: Int
        var title: String =
                if (blackCount > whiteCount) {
                    result = 1
                    "黒の勝ち"
                } else if (blackCount < whiteCount) {
                    result = 2
                    "白の勝ち"
                } else {
                    result = 0
                    "引き分け"
                }

        if (showDialog) {
            showDialog(
                    title = title,
                    message = "黒:${blackCount} 白${64-blackCount}"
            )
        }
        return result
    }
}