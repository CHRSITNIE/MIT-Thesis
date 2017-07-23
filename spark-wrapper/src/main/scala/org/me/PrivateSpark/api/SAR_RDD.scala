package org.me.PrivateSpark.api

import org.apache.spark.{Partitioner, TaskContext, Partition, SparkContext}
import org.apache.spark.annotation.DeveloperApi
import org.apache.spark.rdd.RDD
import org.apache.spark.SparkContext._
import org.me.PrivateSpark.{Laplace, Winsorize}

import scala.collection.Map
import scala.collection.parallel.ParSeq
import scala.reflect.{ClassTag, classTag}

class SAR_RDD[T](_sc : SparkContext, _partitions : ParSeq[RDD[T]], _numPartitions: Int) {

  private val sc = _sc
  private val partitions = _partitions
  private val numPartitions = _numPartitions

  def result[U](transformation: RDD[T] => RDD[U])(implicit x: ClassTag[U]) = {
    new SAR_RDD(sc, partitions.map(transformation), numPartitions)
  }

  def map[U](f: (T) => U)(implicit evidence$3: ClassTag[U]) = result(x => x.map(f))

  def flatMap[U](f: (T) => TraversableOnce[U])(implicit evidence$4: ClassTag[U]) = result(x => x.flatMap(f))

  def filter(f: (T) => Boolean)(implicit evidence: ClassTag[T]) = result(x => x.filter(f))

  def cache()(implicit evidence: ClassTag[T]) = {
    result(x => x.cache())
  }

  def reduce(f: (T, T) => T)(implicit evidence: ClassTag[T]) = {
    // First check the type of T
    partitions.head match {
      case single : RDD[Double @unchecked] if evidence == classTag[Double] =>
        // Result is a single double, so can shortcut. For now just do average
        def _partitions = partitions.asInstanceOf[Seq[RDD[Double]]]
        def _f = f.asInstanceOf[(Double, Double) => Double]
        def results = _partitions.map(x => x.reduce(_f))
        def average = results.sum / results.length
        average
    }
  }

  def median()(implicit evidence : ClassTag[T]) = {
    partitions.head match {
      case single : RDD[Double @unchecked] if evidence == classTag[Double] =>
        val _partitions = partitions.asInstanceOf[Seq[RDD[Double]]]
        val sorted = _partitions.map(x => x.sortBy(x => x))
        val count = _partitions.head.count()
        val zipped = sorted.map(x => x.zipWithIndex.map{case (k,v) => (v,k)})
        val medians : Seq[Double] = zipped.map(x => x.lookup(count / 2).head)
        val result = aggregate(medians)
//        def average_of_medians = medians.sum / medians.length
//        average_of_medians
        result

    }
  }

  def average()(implicit evidence : ClassTag[T]) = {
    partitions.head match {
      case single : RDD[Double @unchecked] if evidence == classTag[Double] =>
        val _partitions = partitions.asInstanceOf[Seq[RDD[Double]]]
        val averages = _partitions.map(x => {
          val count = x.count()
          val sum = x.reduce(_ + _)
          sum / count
        })
        val result = aggregate(averages)
        result

    }
  }

  def aggregate(samples : Seq[Double]) : Double = {
    // TODO
    def epsilon = 0.1

    val lower_crude = Winsorize.private_quantile(samples, 0.25, epsilon / 4)
    val upper_crude = Winsorize.private_quantile(samples, 0.75, epsilon / 4)
    val mean_crude = (lower_crude + upper_crude) / 2
    val range_crude = math.abs(upper_crude - lower_crude)
    val upper = mean_crude + 2*range_crude
    val lower = mean_crude - 2*range_crude
    def winsorize(x : Double) : Double = {
      if (x < lower) { lower }
      else if (x > upper) { upper }
      else { x }
    }
    val w_samples = samples.map(winsorize)
    val w_mean = w_samples.sum / w_samples.length
    val scale = math.abs(upper - lower) / (2 * epsilon * w_samples.length)
    println("Upper: " + upper + ", Lower: " + lower)
    w_mean + Laplace.draw(scale)
//     w_mean
  }

  // TODO everything below

  /*
  def distinct(numPartitions: Int)(implicit evidence: ClassTag[T], ord: Ordering[T]) = result(x => x.distinct(numPartitions))

  def distinct()(implicit evidence: ClassTag[T])= result(x => x.distinct())

  def sample(withReplacement: Boolean, fraction: Double, seed: Long)(implicit evidence: ClassTag[T]) = result(x => x.sample(withReplacement, fraction, seed))

  def sortBy[K](f: (T) => K, ascending: Boolean, numPartitions: Int)(implicit ord: Ordering[K], ctag: ClassTag[K], evidence: ClassTag[T]) =
    result(x => x.sortBy(f, ascending, numPartitions))


  def fold(zeroValue: T)(op: (T, T) => T): T = super.fold(zeroValue)(op)

  def aggregate[U](zeroValue: U)(seqOp: (U, T) => U, combOp: (U, U) => U)(implicit evidence$30: ClassManifest[U]): U = super.aggregate(zeroValue)(seqOp, combOp)

  def count(): Long = super.count()

  def countByValue()(implicit ord: Ordering[T]): Map[T, Long] = super.countByValue()

  def take(num: Int): Array[T] = super.take(num)

  def first(): T = super.first()

  def top(num: Int)(implicit ord: Ordering[T]): Array[T] = super.top(num)

  def takeOrdered(num: Int)(implicit ord: Ordering[T]): Array[T] = super.takeOrdered(num)

  def max()(implicit ord: Ordering[T]): T = super.max()

  def min()(implicit ord: Ordering[T]): T = super.min()

  def groupBy[K](f: (T) => K)(implicit kt: ClassManifest[K]): RDD[(K, Iterable[T])] = super.groupBy(f)

  def groupBy[K](f: (T) => K, numPartitions: Int)(implicit kt: ClassManifest[K]): RDD[(K, Iterable[T])] = super.groupBy(f, numPartitions)

  def groupBy[K](f: (T) => K, p: Partitioner)(implicit kt: ClassManifest[K], ord: Ordering[K]): RDD[(K, Iterable[T])] = super.groupBy(f, p)
  */
}
