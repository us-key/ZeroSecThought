package models.daos

import java.sql.{Date, Timestamp}
import javax.inject.Inject

import models.Memo
import play.api.db.slick.DatabaseConfigProvider
import slick.driver.JdbcProfile
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

/**
  * メモデータアクセスオブジェクト.
  * DB操作方法を追加したい場合はこのクラスにメソッド追加する.
  * Created on 16/10/24.
  *
  * @param dbConfigProvider
  */
class MemoDao @Inject()(dbConfigProvider: DatabaseConfigProvider) {

  val dbConfig = dbConfigProvider.get[JdbcProfile]
  import dbConfig.driver.api._

  implicit def javaDateTimestampMapper = MappedColumnType.base[Date, Timestamp](
    dt => new Timestamp(dt.getTime),
    ts => new Date(ts.getTime)
  )

  /**
    * メモテーブル.
    * @param tag
    */
  private class MemoTable(tag: Tag) extends Table[Memo](tag, "memo") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def parentId = column[Long]("parent_id")
    def title = column[String]("title")
    def content = column[String]("content")
    def createDate = column[java.sql.Date]("create_date")
    def fav = column[String]("fav")
    def * = (id.?, parentId.?, title, content, createDate, fav) <> ((Memo.apply _).tupled, Memo.unapply)
  }

  private val memos = TableQuery[MemoTable]

  /**
    * 全件取得.
    * @return
    */
  def getMemos(): Future[List[Memo]] =
    dbConfig.db.run(memos.sortBy(row => row.title).result).map(_.toList)

  /**
    * 条件検索.
    * @param conditionDateFrom
    * @param conditionDateTo
    * @param conditionTitle
    * @param conditionContent
    * @return
    */
  def findMemos(
    conditionDateFrom: Option[Date],
    conditionDateTo: Option[Date],
    conditionTitle: Option[String],
    conditionContent: Option[String],
    sortKey: Option[String],
    sortOrder: Option[String],
    favChecked: Option[String]): Future[List[Memo]] = {
    dbConfig.db.run(memos.filter( row =>
         (row.createDate >= conditionDateFrom.getOrElse(java.sql.Date.valueOf("1900-01-01")))
      && (row.createDate <= conditionDateTo.getOrElse(java.sql.Date.valueOf("9999-12-31")))
      && (row.title like s"%${conditionTitle.getOrElse("")}%")
      && (row.content like s"%${conditionContent.getOrElse("")}%")
      && (if (favChecked.contains("1")) {
           row.fav === "1"
         } else 1 == 1)
    // ソート
    ).sortBy[slick.lifted.Ordered](
      {
        sortKey match {
          case Some("1") =>
            sortOrder match {
              case Some("1") => row => row.title
              case Some("2") => row => row.title.desc
              case _ => row => row.title
            }
          case Some("2") =>
            sortOrder match {
              case Some("1") => row => row.createDate
              case Some("2") => row => row.createDate.desc
              case _ => row => row.createDate
            }
          case _ =>
            sortOrder match {
              case Some("1") => row => row.title
              case Some("2") => row => row.title.desc
              case _ => row => row.title
            }
        }}).result).map(_.toList)
  }

  /**
    * 指定された日付のメモ件数取得.
    * @param date
    * @return
    */
  def getCount(date: Date): Int =
    Await.result(
      dbConfig.db.run(memos.filter(_.createDate === date).length.result),
      Duration.Inf
    )

  /**
    * IDによる取得.
    * @param id
    * @return
    */
  def byId(id: Long): Future[Option[Memo]] =
    dbConfig.db.run(memos.filter(_.id === id).result.headOption)

  /**
    * CREATE.
    * @param memo
    * @return
    */
  def create(memo: Memo): Future[Int] = {
    // 作成日付にデフォルト値を設定するため敢えて消す
//    dbConfig.db.run(memos += memo)
    dbConfig.db.run( memos.map(m => (m.parentId, m.title, m.content, m.fav))
      += ((memo.parentId.get, memo.title, memo.content, memo.fav)))
  }

  /**
    * UPDATE.
    * @param memo
    * @return
    */
  def update(memo: Memo): Future[Int] = {
    dbConfig.db.run(memos.filter(_.id === memo.id).map(
      m => (
        m.parentId,
        m.title,
        m.content
        )
    ).update(
      memo.parentId.getOrElse(0),
      memo.title,
      memo.content
      )
    )
  }

  /**
    * favのUPDATE.
    * @param id
    * @param fav
    * @return
    */
  def updateFav(id: Long, fav: String): Future[Int] = {
    dbConfig.db.run(memos.filter(_.id === id).map(
      m => (
        m.fav
      )
    ).update(
      fav
      )
    )
  }

  /**
    * DELETE.
    * @param id
    * @return
    */
  def delete(id: Long): Future[Int] =
    dbConfig.db.run(memos.filter(_.id === id).delete)
}
