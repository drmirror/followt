<html>

<link rel="stylesheet" href="/style.css" type="text/css">
<body>
  <div id="content">
   <div id="control">
   <form method=POST action="/report">
     <input type="text" name="screen_name" value="${screen_name!}">
     <select name="interval">
        <option value="10800000" <#if interval == 10800000>selected="true"</#if> >3 hours</option>
        <option value="21600000" <#if interval == 21600000>selected="true"</#if> >6 hours</option>
        <option value="43200000" <#if interval == 43200000>selected="true"</#if> >12 hours</option>
        <option value="86400000" <#if interval == 86400000>selected="true"</#if> >24 hours</option>
        <option value="172800000" <#if interval == 172800000>selected="true"</#if> >48 hours</option>
        <option value="604800000" <#if interval == 604800000>selected="true"</#if> >1 week</option>
     </select>
     <input type="submit">
   </form>
   </div>
     <div id="results">
      <table id="followers">
       <tr>
         <th>name</th>
         <th>following since</th>
       </tr>
       <#list followers as fol>
         <tr>
          <td>
                <a href="http://twitter.com/${fol.followerScreenName}">
                   @${fol.followerScreenName}
                </a>
          </td>
          <td>
                ${fol.followedSince?datetime?string("yyyy-MM-dd HH:mm:ss")}
          </td>
         </tr>
       </#list>
      </table>
                
      <table id="unfollowers">
       <tr>
         <th>name</th>
         <th>last seen</th>
         <th>unfollowed before</th>
         <th>days followed</th>
       </tr>
       <#list unfollowers as unf>
         <tr>
           <td>
                <a href="http://twitter.com/${unf.followerScreenName}">
                   @${unf.followerScreenName}
                </a>
          </td>
          <td>
             <#if unf.lastSeen??>
                ${unf.lastSeen?datetime?string("yyyy-MM-dd HH:mm:ss")}
             </#if>
          </td>
          <td>
                ${unf.unfollowedSince?datetime?string("yyyy-MM-dd HH:mm:ss")}
          </td>
          <td>
                ${unf.followingFor / 86400000.0}    
          </td>
         </tr>  
       </#list>
   </table>
   </div>
 </div>
</body>
</html>