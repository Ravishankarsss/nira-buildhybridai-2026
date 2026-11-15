package com.mos.nira.core.image

import kotlin.math.abs

/**
 * Lawson-Hanson Non-Negative Least Squares (NNLS) solver for 3 x N linear systems.
 *
 * Minimizes ||A * x - y||_2 subject to x >= 0,
 * where A is 3 x N (N ink absorbance vectors), y is 3 x 1 (target pixel absorbance).
 *
 * Reference: Lawson, C.L. and Hanson, R.J., "Solving Least Squares Problems",
 * Prentice-Hall, 1974, Chapter 23.
 */
object LawsonHansonNnls {

    private const val TOLERANCE = 1e-9
    private const val REGULARIZATION = 1e-10

    /**
     * Solves non-negative least squares for A (3 x N) and y (3 x 1).
     * @param aColumns Array of N columns, each of size 3 (absorbance of ink j).
     * @param y Target vector of size 3 (absorbance of pixel).
     * @return Solution array x of size N with x[j] >= 0.0.
     */
    fun solve(aColumns: Array<DoubleArray>, y: DoubleArray): DoubleArray {
        val n = aColumns.size
        require(y.size == 3) { "Target vector y must have dimension 3" }
        if (n == 0) return DoubleArray(0)
        for (col in aColumns) {
            require(col.size == 3) { "Each column in A must have dimension 3" }
        }

        val x = DoubleArray(n)
        val inPassive = BooleanArray(n) { false } // P set

        var outerIter = 0
        val maxOuterIter = 3 * n

        while (outerIter < maxOuterIter) {
            outerIter++

            // Compute residual r = y - A * x
            val r0 = y[0] - computeDot(aColumns, x, 0)
            val r1 = y[1] - computeDot(aColumns, x, 1)
            val r2 = y[2] - computeDot(aColumns, x, 2)

            // Compute dual vector w = A^T * r
            var maxW = -Double.MAX_VALUE
            var bestIdx = -1

            for (j in 0 until n) {
                if (!inPassive[j]) {
                    val wJ = aColumns[j][0] * r0 + aColumns[j][1] * r1 + aColumns[j][2] * r2
                    if (wJ > maxW) {
                        maxW = wJ
                        bestIdx = j
                    }
                }
            }

            // If optimal within tolerance or no more active variables can improve
            if (maxW <= TOLERANCE || bestIdx == -1) {
                break
            }

            // Move bestIdx to passive set P
            inPassive[bestIdx] = true

            // Inner loop
            var innerIter = 0
            while (innerIter < maxOuterIter) {
                innerIter++

                // Extract indices in P
                val pIndices = mutableListOf<Int>()
                for (j in 0 until n) {
                    if (inPassive[j]) pIndices.add(j)
                }

                val k = pIndices.size
                if (k == 0) break

                // Solve unconstrained least squares on A_P: min ||A_P * s_P - y||_2
                // Using Normal Equations: (A_P^T * A_P) * s_P = A_P^T * y
                val sP = solveNormalEquations(aColumns, pIndices, y)

                // Check if all s_j > 0 in P
                var allPositive = true
                for (i in 0 until k) {
                    if (sP[i] <= TOLERANCE) {
                        allPositive = false
                        break
                    }
                }

                if (allPositive) {
                    for (i in 0 until k) {
                        x[pIndices[i]] = sP[i]
                    }
                    break // Inner loop finished
                }

                // Find alpha = min_{j in P, s_j <= 0} (x_j / (x_j - s_j))
                var alpha = 1.0
                for (i in 0 until k) {
                    val j = pIndices[i]
                    val sVal = sP[i]
                    if (sVal <= 0.0) {
                        val ratio = x[j] / (x[j] - sVal)
                        if (ratio < alpha) {
                            alpha = ratio
                        }
                    }
                }

                // Update x = x + alpha * (s - x)
                for (i in 0 until k) {
                    val j = pIndices[i]
                    x[j] += alpha * (sP[i] - x[j])
                    if (abs(x[j]) <= TOLERANCE || x[j] < 0.0) {
                        x[j] = 0.0
                        inPassive[j] = false // Remove from P
                    }
                }
            }
        }

        // Clean up any tiny floating point negatives
        for (j in 0 until n) {
            if (x[j] < 0.0) x[j] = 0.0
        }

        return x
    }

    private fun computeDot(aColumns: Array<DoubleArray>, x: DoubleArray, row: Int): Double {
        var sum = 0.0
        for (j in aColumns.indices) {
            sum += aColumns[j][row] * x[j]
        }
        return sum
    }

    /**
     * Solves (A_P^T * A_P) * s = A_P^T * y for k <= 3 variables.
     */
    private fun solveNormalEquations(
        aColumns: Array<DoubleArray>,
        pIndices: List<Int>,
        y: DoubleArray
    ): DoubleArray {
        val k = pIndices.size
        val m = Array(k) { DoubleArray(k) }
        val rhs = DoubleArray(k)

        for (i in 0 until k) {
            val colI = aColumns[pIndices[i]]
            rhs[i] = colI[0] * y[0] + colI[1] * y[1] + colI[2] * y[2]
            for (j in 0 until k) {
                val colJ = aColumns[pIndices[j]]
                var dot = colI[0] * colJ[0] + colI[1] * colJ[1] + colI[2] * colJ[2]
                if (i == j) {
                    dot += REGULARIZATION // Small Tikhonov regularization for numerical stability
                }
                m[i][j] = dot
            }
        }

        return when (k) {
            1 -> {
                doubleArrayOf(rhs[0] / m[0][0])
            }
            2 -> {
                solve2x2(m, rhs)
            }
            3 -> {
                solve3x3(m, rhs)
            }
            else -> {
                // If k > 3 (cannot exceed 3 in 3D system, but safe fallback)
                solveGeneral(m, rhs)
            }
        }
    }

    private fun solve2x2(m: Array<DoubleArray>, rhs: DoubleArray): DoubleArray {
        val a = m[0][0]
        val b = m[0][1]
        val c = m[1][0]
        val d = m[1][1]
        val det = a * d - b * c
        if (abs(det) < 1e-14) {
            return doubleArrayOf(rhs[0] / (a + 1e-8), 0.0)
        }
        val x0 = (d * rhs[0] - b * rhs[1]) / det
        val x1 = (-c * rhs[0] + a * rhs[1]) / det
        return doubleArrayOf(x0, x1)
    }

    private fun solve3x3(m: Array<DoubleArray>, rhs: DoubleArray): DoubleArray {
        val a = m[0][0]; val b = m[0][1]; val c = m[0][2]
        val d = m[1][0]; val e = m[1][1]; val f = m[1][2]
        val g = m[2][0]; val h = m[2][1]; val i = m[2][2]

        val det = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g)
        if (abs(det) < 1e-14) {
            return solve2x2(
                arrayOf(doubleArrayOf(a, b), doubleArrayOf(d, e)),
                doubleArrayOf(rhs[0], rhs[1])
            ) + doubleArrayOf(0.0)
        }

        val det0 = rhs[0] * (e * i - f * h) - b * (rhs[1] * i - f * rhs[2]) + c * (rhs[1] * h - e * rhs[2])
        val det1 = a * (rhs[1] * i - f * rhs[2]) - rhs[0] * (d * i - f * g) + c * (d * rhs[2] - rhs[1] * g)
        val det2 = a * (e * rhs[2] - rhs[1] * h) - b * (d * rhs[2] - rhs[1] * g) + rhs[0] * (d * h - e * g)

        return doubleArrayOf(det0 / det, det1 / det, det2 / det)
    }

    private fun solveGeneral(m: Array<DoubleArray>, rhs: DoubleArray): DoubleArray {
        val k = rhs.size
        // Simple Gaussian elimination with partial pivoting
        val a = Array(k) { row -> DoubleArray(k + 1) { col -> if (col < k) m[row][col] else rhs[row] } }

        for (p in 0 until k) {
            var maxRow = p
            var maxVal = abs(a[p][p])
            for (row in p + 1 until k) {
                if (abs(a[row][p]) > maxVal) {
                    maxVal = abs(a[row][p])
                    maxRow = row
                }
            }
            val temp = a[p]
            a[p] = a[maxRow]
            a[maxRow] = temp

            if (abs(a[p][p]) < 1e-12) a[p][p] = 1e-12

            for (row in p + 1 until k) {
                val factor = a[row][p] / a[p][p]
                for (col in p until k + 1) {
                    a[row][col] -= factor * a[p][col]
                }
            }
        }

        val res = DoubleArray(k)
        for (row in k - 1 downTo 0) {
            var sum = a[row][k]
            for (col in row + 1 until k) {
                sum -= a[row][col] * res[col]
            }
            res[row] = sum / a[row][row]
        }
        return res
    }
}
