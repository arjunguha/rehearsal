﻿@using System.Security.Claims
@model System.Security.Principal.IPrincipal

@{
    ViewBag.Title = "Identity";
    Layout = "~/Views/Shared/_Layout.cshtml";
}


<ol class="round">

    <li class="one">
        <h5>Principal</h5>

        Username: @Model.Identity.Name
        <br />
        Is authenticated: @Model.Identity.IsAuthenticated
        <br />
        Authentication type: @Model.Identity.AuthenticationType
    </li>

    <li class="two">
        <h5>Types</h5>

        Principal type: @Model.GetType().FullName
        <br />
        Principal base type: @Model.GetType().BaseType.FullName
        <br />
        Identity type: @Model.Identity.GetType().FullName
        <br />
        Identity base type: @Model.Identity.GetType().BaseType.FullName
        <br />
    </li>

    @if (Model is ClaimsPrincipal)
    {
        var id = Model as ClaimsPrincipal;
        
        <li class="three">
            <h5>Claims</h5>

            @foreach (var claim in id.Claims)
            {
                <b>@claim.Type</b>
                <br />
                @claim.Value
                <br />
                @claim.Issuer @:(@claim.OriginalIssuer)
                <br />
                <br />
            }
                    
        </li>
    }

    

</ol>