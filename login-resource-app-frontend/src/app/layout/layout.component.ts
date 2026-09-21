import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { SidebarComponent } from '../sidebar/sidebar.component';

// Shell for every page behind the login: sidebar on the left, the active routed page on
// the right. Applied once in app.routes.ts as the parent of every protected child route,
// so the sidebar never has to be re-added to each individual page.
@Component({
  selector: 'app-layout',
  templateUrl: './layout.component.html',
  styleUrl: './layout.component.css',
  imports: [RouterOutlet, SidebarComponent]
})
export class LayoutComponent {}
