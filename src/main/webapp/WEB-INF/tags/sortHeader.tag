<%@ tag pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%--
  Renders one sortable column header.
  Clicking a new column starts ascending; clicking the active column flips it.
--%>
<%@ attribute name="key"         required="true"  type="java.lang.String" %>
<%@ attribute name="label"       required="true"  type="java.lang.String" %>
<%@ attribute name="sort"        required="true"  type="java.lang.String" %>
<%@ attribute name="dir"         required="true"  type="java.lang.String" %>
<%@ attribute name="filterQuery" required="true"  type="java.lang.String" %>
<%@ attribute name="numeric"     required="false" type="java.lang.Boolean" %>

<c:set var="active" value="${sort eq key}" />
<c:set var="nextDir" value="${active and dir eq 'asc' ? 'desc' : 'asc'}" />
<%-- Build the class list in ONE expression. Splitting it across two and
     relying on a literal space between them breaks under
     trimDirectiveWhitespaces, which strips whitespace-only template text and
     silently yields class="numsorted". --%>
<c:set var="thClass" value="${numeric ? 'num' : ''}${active ? ' sorted' : ''}" />

<th class="${thClass}"
    <c:if test="${active}">aria-sort="${dir eq 'asc' ? 'ascending' : 'descending'}"</c:if>>
  <a class="sort-link"
     href="<c:url value='/report'/>?${filterQuery}&amp;generate=1&amp;page=1&amp;sort=${key}&amp;dir=${nextDir}"
     title="Sort by ${label} (${nextDir})">
    <span>${label}</span>
    <span class="indicator">
      <c:choose>
        <c:when test="${active and dir eq 'asc'}">&#9650;</c:when>
        <c:when test="${active}">&#9660;</c:when>
        <c:otherwise>&#8693;</c:otherwise>
      </c:choose>
    </span>
  </a>
</th>
