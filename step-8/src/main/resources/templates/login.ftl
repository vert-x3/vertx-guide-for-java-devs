<#include "header.ftl">

<div class="row">

  <div class="col-md-12 mt-1">
    <form action="/login-auth" method="POST">
      <div class="form-group">
        <input type="text" name="username" placeholder="login">
        <input type="password" name="password" placeholder="password">
        <input type="hidden" name="return_url" value="/">
        <button type="submit" class="btn btn-primary">Login</button>
      </div>
    </form>
  </div>

</div>

<#include "footer.ftl">
