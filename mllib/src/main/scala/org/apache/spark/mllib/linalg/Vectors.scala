/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark.mllib.linalg

import java.lang.{Iterable => JavaIterable, Integer => JavaInteger, Double => JavaDouble}
import java.util.Arrays

import scala.annotation.varargs
import scala.collection.JavaConverters._

import breeze.linalg.{Vector => BV, DenseVector => BDV, SparseVector => BSV}

/**
 * Represents a numeric vector, whose index type is Int and value type is Double.
 */
trait Vector extends Serializable {

  /**
   * Size of the vector.
   */
  def size: Int

  /**
   * Converts the instance to a double array.
   */
  def toArray: Array[Double]

  override def equals(other: Any): Boolean = {
    other match {
      case v: Vector =>
        Arrays.equals(this.toArray, v.toArray)
      case _ => false
    }
  }

  override def hashCode(): Int = Arrays.hashCode(this.toArray)

  /**
   * Converts the instance to a breeze vector.
   */
  private[mllib] def toBreeze: BV[Double]
}

/**
 * Factory methods for [[org.apache.spark.mllib.linalg.Vector]].
 */
object Vectors {

  /**
   * Creates a dense vector.
   */
  @varargs
  def dense(firstValue: Double, otherValues: Double*): Vector =
    new DenseVector((firstValue +: otherValues).toArray)

  // A dummy implicit is used to avoid signature collision with the one generated by @varargs.
  /**
   * Creates a dense vector from a double array.
   */
  def dense(values: Array[Double]): Vector = new DenseVector(values)

  /**
   * Creates a sparse vector providing its index array and value array.
   *
   * @param size vector size.
   * @param indices index array, must be strictly increasing.
   * @param values value array, must have the same length as indices.
   */
  def sparse(size: Int, indices: Array[Int], values: Array[Double]): Vector =
    new SparseVector(size, indices, values)

  /**
   * Creates a sparse vector using unordered (index, value) pairs.
   *
   * @param size vector size.
   * @param elements vector elements in (index, value) pairs.
   */
  def sparse(size: Int, elements: Seq[(Int, Double)]): Vector = {
    require(size > 0)

    val (indices, values) = elements.sortBy(_._1).unzip
    var prev = -1
    indices.foreach { i =>
      require(prev < i, s"Found duplicate indices: $i.")
      prev = i
    }
    require(prev < size)

    new SparseVector(size, indices.toArray, values.toArray)
  }

  /**
   * Creates a sparse vector using unordered (index, value) pairs in a Java friendly way.
   *
   * @param size vector size.
   * @param elements vector elements in (index, value) pairs.
   */
  def sparse(size: Int, elements: JavaIterable[(JavaInteger, JavaDouble)]): Vector = {
    sparse(size, elements.asScala.map { case (i, x) =>
      (i.intValue(), x.doubleValue())
    }.toSeq)
  }

  /**
   * Creates a vector instance from a breeze vector.
   */
  private[mllib] def fromBreeze(breezeVector: BV[Double]): Vector = {
    breezeVector match {
      case v: BDV[Double] =>
        require(v.offset == 0, s"Do not support non-zero offset ${v.offset}.")
        require(v.stride == 1, s"Do not support stride other than 1, but got ${v.stride}.")
        new DenseVector(v.data)
      case v: BSV[Double] =>
        new SparseVector(v.length, v.index, v.data)
      case v: BV[_] =>
        sys.error("Unsupported Breeze vector type: " + v.getClass.getName)
    }
  }
}

/**
 * A dense vector represented by a value array.
 */
class DenseVector(val values: Array[Double]) extends Vector {

  override def size: Int = values.length

  override def toString: String = values.mkString("[", ",", "]")

  override def toArray: Array[Double] = values

  private[mllib] override def toBreeze: BV[Double] = new BDV[Double](values)
}

/**
 * A sparse vector represented by an index array and an value array.
 *
 * @param n size of the vector.
 * @param indices index array, assume to be strictly increasing.
 * @param values value array, must have the same length as the index array.
 */
class SparseVector(val n: Int, val indices: Array[Int], val values: Array[Double]) extends Vector {

  override def size: Int = n

  override def toString: String = {
    "(" + n + "," + indices.zip(values).mkString("[", "," ,"]") + ")"
  }

  override def toArray: Array[Double] = {
    val data = new Array[Double](n)
    var i = 0
    val nnz = indices.length
    while (i < nnz) {
      data(indices(i)) = values(i)
      i += 1
    }
    data
  }

  private[mllib] override def toBreeze: BV[Double] = new BSV[Double](indices, values, n)
}
