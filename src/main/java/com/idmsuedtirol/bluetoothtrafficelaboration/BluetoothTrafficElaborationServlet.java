/*

BluetoothTrafficElaboration: various elaborations of traffic data

Copyright (C) 2017 IDM Südtirol - Alto Adige - Italy

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

*/

package com.idmsuedtirol.bluetoothtrafficelaboration;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Properties;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @author Davide Montesin <d@vide.bz>
 */
public class BluetoothTrafficElaborationServlet extends HttpServlet
{
   private static final String JDBC_CONNECTION_STRING = "JDBC_CONNECTION_STRING";
   private static final String JDBC_CONNECTION_DRIVER = "JDBC_CONNECTION_DRIVER";
   private final Properties props = new Properties();
   DatabaseHelper              databaseHelper;

   TaskThread                  taskThread;

   @Override
   public void init(ServletConfig config) throws ServletException
   {
      try
      {
         super.init(config);
         props.load(new FileInputStream("classpath:app.properties"));
         String jdbcUrl = System.getProperty(props.getProperty("jdbc.connectionString"));
         // TODO driver as system parameter
         String driver = "org.postgresql.Driver"; // System.getProperty(JDBC_CONNECTION_DRIVER);
         this.databaseHelper = new DatabaseHelper(driver, jdbcUrl);

         this.taskThread = new TaskThread(this.databaseHelper);
         this.taskThread.start();
      }
      catch (Exception exxx)
      {
         throw new ServletException(exxx);
      }

   }

   @Override
   protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
   {
      try
      {
         ElaborationsInfo elaborationsInfo = new ElaborationsInfo();
         elaborationsInfo.taskThreadAlive = this.taskThread.isAlive();
         synchronized (this.taskThread.exclusiveLock)
         {
            elaborationsInfo.tashThreadRunning = !this.taskThread.sleeping;
            elaborationsInfo.sleepingUntil = this.taskThread.sleepingUntil;
         }

         elaborationsInfo.tasks.addAll(this.databaseHelper.newSelectTaskInfo());

         ObjectMapper mapper = new ObjectMapper();
         mapper.setVisibility(mapper.getVisibilityChecker().withFieldVisibility(Visibility.NON_PRIVATE));
         StringWriter sw = new StringWriter();
         mapper.writeValue(sw, elaborationsInfo);
         resp.getWriter().write(sw.toString());
      }
      catch (Exception exxx)
      {
         throw new ServletException(exxx);
      }
   }

   @Override
   public void destroy()
   {
      super.destroy();
      this.taskThread.interrupt();
      try
      {
         this.taskThread.join();
      }
      catch (InterruptedException e)
      {
         // TODO should never happens: notify crashbox or throw a RuntimeException
         e.printStackTrace();
      }
   }
}
