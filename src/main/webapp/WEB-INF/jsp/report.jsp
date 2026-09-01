<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c"    uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt"  uri="jakarta.tags.fmt" %>
<%@ taglib prefix="fn"   uri="jakarta.tags.functions" %>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<%@ taglib prefix="ui"   tagdir="/WEB-INF/tags" %>
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Game Transaction Report</title>
  <link rel="stylesheet" href="<c:url value='/css/app.css'/>">
</head>
<body>

<header class="topbar">
  <h1>Game Transaction Report</h1>
  <p class="subtitle">Transactions in a date range, with per-column filters, sorting and paging.</p>
</header>

<main>

  <%-- ------------------------------------------------------------------ --%>
  <%-- A) Search form                                                      --%>
  <%-- ------------------------------------------------------------------ --%>
  <form:form method="get" action="${pageContext.request.contextPath}/report"
             modelAttribute="criteria" cssClass="card search-form">
    <input type="hidden" name="generate" value="1">
    <input type="hidden" name="sort" value="${criteria.sort}">
    <input type="hidden" name="dir" value="${criteria.dir}">
    <input type="hidden" name="page" value="1">

    <fieldset>
      <legend>Date range <span class="required">required</span></legend>
      <div class="grid">
        <div class="field">
          <label for="startDateTime">Start date/time</label>
          <form:input path="startDateTime" id="startDateTime" type="datetime-local" step="1" required="required"/>
          <c:if test="${submitted}"><form:errors path="startDateTime" cssClass="error"/></c:if>
        </div>
        <div class="field">
          <label for="endDateTime">End date/time</label>
          <form:input path="endDateTime" id="endDateTime" type="datetime-local" step="1" required="required"/>
          <c:if test="${submitted}"><form:errors path="endDateTime" cssClass="error"/></c:if>
        </div>
        <div class="field">
          <label for="accountId">Account ID <span class="hint">optional</span></label>
          <form:input path="accountId" id="accountId" type="number" min="0" placeholder="e.g. 2203"/>
          <c:if test="${submitted}"><form:errors path="accountId" cssClass="error"/></c:if>
        </div>
      </div>
    </fieldset>

    <%-- --------------------------------------------------------------- --%>
    <%-- D) Per-column filters                                            --%>
    <%-- --------------------------------------------------------------- --%>
    <fieldset>
      <legend>Column filters <span class="hint">optional &mdash; prefix match</span></legend>
      <div class="grid">
        <div class="field">
          <label for="platformTranId">Platform tran ID</label>
          <form:input path="platformTranId" id="platformTranId" placeholder="e.g. 500010"/>
        </div>
        <div class="field">
          <label for="gameTranId">Game tran ID</label>
          <form:input path="gameTranId" id="gameTranId" placeholder="e.g. 110331"/>
        </div>
        <div class="field">
          <label for="gameId">Game ID</label>
          <form:input path="gameId" id="gameId" placeholder="e.g. 429"/>
        </div>
        <div class="field">
          <label for="tranType">Tran type</label>
          <form:input path="tranType" id="tranType" list="tranTypeOptions" placeholder="e.g. GAME_BET"/>
          <datalist id="tranTypeOptions">
            <c:forEach var="t" items="${tranTypes}"><option value="${t}"></option></c:forEach>
          </datalist>
        </div>
        <div class="field">
          <label for="size">Page size</label>
          <form:select path="size" id="size">
            <c:forEach var="s" items="${pageSizes}">
              <form:option value="${s}"/>
            </c:forEach>
          </form:select>
        </div>
      </div>
    </fieldset>

    <div class="actions">
      <button type="submit" class="primary">Generate Report</button>
      <a class="button ghost" href="<c:url value='/report'/>">Reset</a>
    </div>

    <c:if test="${submitted}">
      <form:errors cssClass="error banner"/>
    </c:if>
  </form:form>

  <c:if test="${submitted and not empty reportPage}">

    <%-- --------------------------------------------------------------- --%>
    <%-- Bonus: summary section                                           --%>
    <%-- --------------------------------------------------------------- --%>
    <section class="cards">
      <div class="card stat">
        <span class="stat-label">Transactions</span>
        <span class="stat-value"><fmt:formatNumber value="${reportPage.totalRows}" type="number"/></span>
      </div>
      <div class="card stat">
        <span class="stat-label">Bet total</span>
        <span class="stat-value"><fmt:formatNumber value="${reportPage.summary.betSum}" type="number" minFractionDigits="2" maxFractionDigits="2"/></span>
      </div>
      <div class="card stat">
        <span class="stat-label">Win total</span>
        <span class="stat-value"><fmt:formatNumber value="${reportPage.summary.winSum}" type="number" minFractionDigits="2" maxFractionDigits="2"/></span>
      </div>
      <div class="card stat">
        <span class="stat-label">Net (win &minus; bet)</span>
        <span class="stat-value ${reportPage.summary.net.signum() < 0 ? 'negative' : 'positive'}">
          <fmt:formatNumber value="${reportPage.summary.net}" type="number" minFractionDigits="2" maxFractionDigits="2"/>
        </span>
      </div>
    </section>

    <section class="card">
      <div class="toolbar">
        <div class="result-count">
          <c:choose>
            <c:when test="${reportPage.totalRows == 0}">No matching transactions.</c:when>
            <c:otherwise>
              Showing
              <strong><fmt:formatNumber value="${reportPage.firstRowNumber}"/></strong>&ndash;<strong><fmt:formatNumber value="${reportPage.lastRowNumber}"/></strong>
              of <strong><fmt:formatNumber value="${reportPage.totalRows}"/></strong> transactions
            </c:otherwise>
          </c:choose>
        </div>
        <div class="toolbar-actions">
          <a class="button"
             href="<c:url value='/report.csv'/>?${filterQuery}&amp;sort=${criteria.sort}&amp;dir=${criteria.dir}">
            Export CSV
          </a>
        </div>
      </div>

      <%-- ------------------------------------------------------------- --%>
      <%-- B) Report table  /  C) Sorting                                 --%>
      <%-- ------------------------------------------------------------- --%>
      <div class="table-wrap">
        <table class="report">
          <thead>
          <tr>
            <ui:sortHeader key="id"             label="id"               sort="${criteria.sort}" dir="${criteria.dir}" filterQuery="${filterQuery}" numeric="true"/>
            <ui:sortHeader key="accountId"      label="account_id"       sort="${criteria.sort}" dir="${criteria.dir}" filterQuery="${filterQuery}" numeric="true"/>
            <ui:sortHeader key="datetime"       label="datetime"         sort="${criteria.sort}" dir="${criteria.dir}" filterQuery="${filterQuery}"/>
            <ui:sortHeader key="tranType"       label="tran_type"        sort="${criteria.sort}" dir="${criteria.dir}" filterQuery="${filterQuery}"/>
            <ui:sortHeader key="platformTranId" label="platform_tran_id" sort="${criteria.sort}" dir="${criteria.dir}" filterQuery="${filterQuery}"/>
            <ui:sortHeader key="gameTranId"     label="game_tran_id"     sort="${criteria.sort}" dir="${criteria.dir}" filterQuery="${filterQuery}"/>
            <ui:sortHeader key="gameId"         label="game_id"          sort="${criteria.sort}" dir="${criteria.dir}" filterQuery="${filterQuery}"/>
            <ui:sortHeader key="amount"         label="amount"           sort="${criteria.sort}" dir="${criteria.dir}" filterQuery="${filterQuery}" numeric="true"/>
            <ui:sortHeader key="balance"        label="balance"          sort="${criteria.sort}" dir="${criteria.dir}" filterQuery="${filterQuery}" numeric="true"/>
          </tr>
          </thead>
          <tbody>
          <c:forEach var="row" items="${reportPage.rows}">
            <tr>
              <td class="num">${row.id}</td>
              <td class="num">${row.accountId}</td>
              <td class="nowrap">${row.datetimeText}</td>
              <td><span class="tag tag-${fn:toLowerCase(row.tranType)}">${row.tranType}</span></td>
              <td class="mono">${row.platformTranId}</td>
              <td class="mono">${row.gameTranId}</td>
              <td>${row.gameId}</td>
              <td class="num ${row.amount.signum() < 0 ? 'negative' : ''}"><fmt:formatNumber value="${row.amount}" type="number" minFractionDigits="2" maxFractionDigits="2"/></td>
              <td class="num"><fmt:formatNumber value="${row.balance}" type="number" minFractionDigits="2" maxFractionDigits="2"/></td>
            </tr>
          </c:forEach>
          <c:if test="${empty reportPage.rows}">
            <tr><td class="empty" colspan="9">No transactions match these criteria.</td></tr>
          </c:if>
          </tbody>
        </table>
      </div>

      <%-- ------------------------------------------------------------- --%>
      <%-- E) Pagination                                                  --%>
      <%-- ------------------------------------------------------------- --%>
      <c:if test="${reportPage.totalPages > 1}">
        <c:set var="base" value="${pageContext.request.contextPath}/report?${filterQuery}&amp;generate=1&amp;sort=${criteria.sort}&amp;dir=${criteria.dir}"/>
        <c:set var="windowStart" value="${reportPage.page - 2 < 1 ? 1 : reportPage.page - 2}"/>
        <c:set var="windowEnd" value="${windowStart + 4 > reportPage.totalPages ? reportPage.totalPages : windowStart + 4}"/>

        <nav class="pagination" aria-label="Report pages">
          <c:choose>
            <c:when test="${reportPage.hasPrevious}">
              <a class="page" href="${base}&amp;page=1">&laquo; First</a>
              <a class="page" href="${base}&amp;page=${reportPage.page - 1}">&lsaquo; Prev</a>
            </c:when>
            <c:otherwise>
              <span class="page disabled">&laquo; First</span>
              <span class="page disabled">&lsaquo; Prev</span>
            </c:otherwise>
          </c:choose>

          <c:forEach var="p" begin="${windowStart}" end="${windowEnd}">
            <c:choose>
              <c:when test="${p == reportPage.page}"><span class="page current">${p}</span></c:when>
              <c:otherwise><a class="page" href="${base}&amp;page=${p}">${p}</a></c:otherwise>
            </c:choose>
          </c:forEach>

          <c:choose>
            <c:when test="${reportPage.hasNext}">
              <a class="page" href="${base}&amp;page=${reportPage.page + 1}">Next &rsaquo;</a>
              <a class="page" href="${base}&amp;page=${reportPage.totalPages}">Last &raquo;</a>
            </c:when>
            <c:otherwise>
              <span class="page disabled">Next &rsaquo;</span>
              <span class="page disabled">Last &raquo;</span>
            </c:otherwise>
          </c:choose>

          <span class="page-of">Page ${reportPage.page} of ${reportPage.totalPages}</span>
        </nav>
      </c:if>
    </section>
  </c:if>

</main>

<%-- The money columns are discovered at startup, so spell out exactly what
     the two derived columns are made of rather than leaving it implicit. --%>
<footer class="footer">
  <div>
    <code>amount</code> =
    <c:forEach var="col" items="${amountColumns}" varStatus="st"><c:if test="${not st.first}"> + </c:if>${col}</c:forEach>
    <c:if test="${empty amountColumns}">0</c:if>
  </div>
  <div>
    <code>balance</code> =
    <c:forEach var="col" items="${balanceColumns}" varStatus="st"><c:if test="${not st.first}"> + </c:if>${col}</c:forEach>
    <c:if test="${empty balanceColumns}">0</c:if>
  </div>
  <div class="footer-note">
    Column filters are prefix matches &middot; bet total is reported as a magnitude
    (wagers are stored as negative amounts), so <code>net = win &minus; bet</code>
  </div>
</footer>

</body>
</html>
