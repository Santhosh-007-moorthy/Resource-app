import { Routes } from '@angular/router';
import { LoginComponent } from './login/login.component';
import { LayoutComponent } from './layout/layout.component';
import { ResourceComponent } from './resource/resource.component';
import { TaskMasterComponent } from './task-master/task-master.component';
import { RecordsComponent } from './records/records.component';
import { AssignedTaskComponent } from './assigned-task/assigned-task.component';
import { ReportTaskComponent } from './report-task/report-task.component';
import { authGuard } from './services/auth.guard';
import { adminGuard } from './services/admin.guard';
import { userGuard } from './services/user.guard';

export const routes: Routes = [
	{ path: '', component: LoginComponent },
	{
		path: '',
		component: LayoutComponent,
		canActivate: [authGuard],
		children: [
			// Admin-only pages, listed in the sidebar as Resource / Task Master / Records.
			{ path: 'resources', component: ResourceComponent, canActivate: [adminGuard] },
			{ path: 'task-master', component: TaskMasterComponent, canActivate: [adminGuard] },
			{ path: 'records', component: RecordsComponent, canActivate: [adminGuard] },
			// Resource-person-only pages, listed in the sidebar as Assigned Task / Report Task.
			{ path: 'assigned-task', component: AssignedTaskComponent, canActivate: [userGuard] },
			{ path: 'report-task', component: ReportTaskComponent, canActivate: [userGuard] }
		]
	},
	{ path: '**', redirectTo: '' }
];
