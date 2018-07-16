

JSP包含其它页面
* 静态方式: 
    * ``` <%@ include file="*.jsp"%> ```
    * 相对路径是相对该JSP所在位置
    * 先包含再编译, jsp为一个整体, 被包含的jsp可访问该jsp的变量
* 动态方式: 
    * ``` <jsp:include page="*.jsp"> ```
    * ``` Request.requestDispatcher("*.jsp").include(request, response); 
    * 先编译再包含, jsp不是一个整体, 不可互访变量

}