@model $rootnamespace$.Models.Accounts.LoginModel

@{
    ViewBag.Title = "Log in";
}

<hgroup class="title">
    <h1>@ViewBag.Title.</h1>
    <h2>Enter your user name and password below.</h2>
</hgroup>

<script src="~/Scripts/jquery.validate.min.js"></script>
<script src="~/Scripts/jquery.validate.unobtrusive.min.js"></script>

@using (Html.BeginForm((string)ViewBag.FormAction, "Account")) {
    @Html.ValidationSummary(true, "Log in was unsuccessful. Please correct the errors and try again.")

    <fieldset>
        <legend>Log in Form</legend>
        <ol>
            <li>
                @Html.LabelFor(m => m.UserName)
                @Html.TextBoxFor(m => m.UserName)
                @Html.ValidationMessageFor(m => m.UserName)
            </li>
            <li>
                @Html.LabelFor(m => m.Password)
                @Html.PasswordFor(m => m.Password)
                @Html.ValidationMessageFor(m => m.Password)
            </li>
            <li>
                @Html.CheckBoxFor(m => m.RememberMe)
                @Html.LabelFor(m => m.RememberMe, new { @class = "checkbox" })
            </li>
        </ol>
        <input type="submit" value="Log in" />
    </fieldset>
    <p>
        @Html.ActionLink("Register", "Register") if you don't have an account.
    </p>
}
