package controllers

import javax.inject.Inject

import forms.{MemoSearchForms, MemoSearchForm}
import models.Memo
import models.daos.MemoDao
import play.api.Logger
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, Controller}
import scala.concurrent.Future
import play.api.libs.concurrent.Execution.Implicits.defaultContext

/**
  * Created on 16/10/14.
  */
class ApplicationController @Inject() (
  val messagesApi: MessagesApi,
  dao: MemoDao,
  implicit val webJarAssets: WebJarAssets)
  extends Controller with I18nSupport {

  def index = Action.async { implicit request =>
    Logger.debug("***** access index *****")
    dao.getMemos().flatMap(memos =>
      Future.successful(Ok(views.html.thoughtMemoList(
        memos,
        MemoSearchForms.memoSearchForm
      )))
    )
  }

  def search = Action.async { implicit request =>
    MemoSearchForms.memoSearchForm.bindFromRequest.fold(
      formWithErrors => {
        Future(BadRequest(views.html.thoughtMemoList(
          null, formWithErrors
        )))
      },
      formValue => {
        dao.findMemos(
          formValue.conditionDateFrom,
          formValue.conditionDateTo,
          formValue.conditionTitle,
          formValue.conditionContent
        ).flatMap(memos =>
          Future.successful(Ok(views.html.thoughtMemoList(
            memos,
            MemoSearchForms.memoSearchForm.fill(
              MemoSearchForm(
                formValue.conditionDateFrom,
                formValue.conditionDateTo,
                formValue.conditionTitle,
                formValue.conditionContent
              )
            ))))
        )
      }
    )
  }

}