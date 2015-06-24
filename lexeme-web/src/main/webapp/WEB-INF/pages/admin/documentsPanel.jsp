<%@ include file="../header.jsp"%>

<div class="page-content">
	<div class="container">
		<section id="content">
			<div class="col-md-12">
				<blockquote>
					<h1 style="text-align: center;">Document Verification Panel</h1>
				</blockquote>
			</div>
			<!-- Personal Information -->
			<table class="table table-hover">
				<thead>
					<tr>
						<th>Document No.</th>
						<th>Document Name</th>
						<th>Action</th>
					</tr>
				</thead>
				<tbody>
				<c:forEach items="${documents}" var="document" varStatus="ctr">
				<tr>
						<td>${ctr.index+1}</td>
						<td>${document.name}</td>
						<td>
							<div class="col-md-3 ">
								<form name="form" method="POST" class="ui form"
									action="${pageContext.request.contextPath}/admin/docs/verify"
									>
									<input type="hidden" name="docId" value="${document.documentId}" />
 									<input type="submit" class="btn btn-success">Verify</button>
								</form>
							</div>
							<div class="col-md-3">
								<form name="form" method="POST" class="ui form"
									action="${pageContext.request.contextPath}/admin/docs/del"
									>
									<input type="hidden" name="docId" value="${document.documentId}" />
									<input type="submit" class="btn btn-danger">Delete</button>
								</form>								
							</div>

						</td>
					</tr>
				
				</c:forEach>
				</tbody>
			</table>
		</section>
	</div>
</div>


<%@ include file="../footer.jsp"%>
