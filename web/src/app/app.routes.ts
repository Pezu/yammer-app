import { Routes } from '@angular/router';
import { Login } from './features/auth/login/login';
import { authGuard } from './core/auth.guard';
import { superGuard } from './core/super.guard';

export const routes: Routes = [
  { path: 'login', component: Login },
  {
    // Scanned from a user's QR-login code (Users → QR): signs in directly.
    // Namespaced under /login — other QR types (e.g. order points) get their own prefixes.
    path: 'login/qr/:token',
    loadComponent: () => import('./features/auth/qr-login/qr-login').then((m) => m.QrLogin),
  },
  {
    // public customer landing behind the printed QR codes — no login
    path: 'customer/order-point/:id',
    loadComponent: () =>
      import('./features/customer/customer-order-point-page').then(
        (m) => m.CustomerOrderPointPage,
      ),
  },
  {
    // the Netopia gateway sends the customer's browser back here (?ref=)
    path: 'customer/order-point/:id/payment-return',
    loadComponent: () =>
      import('./features/customer/payment-return-page').then((m) => m.PaymentReturnPage),
  },
  {
    // Service kanban board (kitchen/bar stations) — SERVICE users land here.
    path: 'service',
    canActivate: [authGuard],
    loadComponent: () => import('./features/service/service-page').then((m) => m.ServicePage),
  },
  {
    path: 'backoffice',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/backoffice/backoffice-layout').then((m) => m.BackofficeLayout),
    children: [
      {
        path: 'clients',
        canActivate: [superGuard],
        loadComponent: () =>
          import('./features/backoffice/pages/clients/clients-page').then((m) => m.ClientsPage),
      },
      {
        path: 'users',
        loadComponent: () =>
          import('./features/backoffice/pages/users/users-page').then((m) => m.UsersPage),
      },
      {
        path: 'locations',
        loadComponent: () =>
          import('./features/backoffice/pages/locations/locations-page').then(
            (m) => m.LocationsPage,
          ),
      },
      {
        path: 'products',
        loadComponent: () =>
          import('./features/backoffice/pages/products/products-page').then((m) => m.ProductsPage),
      },
      {
        path: 'recipes',
        loadComponent: () =>
          import('./features/backoffice/pages/recipes/recipes-page').then((m) => m.RecipesPage),
      },
      {
        path: 'menu',
        loadComponent: () =>
          import('./features/backoffice/pages/menu/menu-page').then((m) => m.MenuPage),
      },
      {
        path: 'order-points',
        loadComponent: () =>
          import('./features/backoffice/pages/order-points/order-points-page').then(
            (m) => m.OrderPointsPage,
          ),
      },
      {
        // "Peripherals" in the UI — printers and cash registers (the old Integrations page).
        path: 'peripherals',
        loadComponent: () =>
          import('./features/backoffice/pages/integrations/integrations-page').then(
            (m) => m.IntegrationsPage,
          ),
      },
      {
        path: 'order-point-types',
        canActivate: [superGuard],
        loadComponent: () =>
          import('./features/backoffice/pages/order-point-types/order-point-types-page').then(
            (m) => m.OrderPointTypesPage,
          ),
      },
      {
        path: 'reports/orders',
        loadComponent: () =>
          import('./features/backoffice/pages/reports/orders-report-page').then(
            (m) => m.OrdersReportPage,
          ),
      },
      {
        path: 'reports/payments',
        loadComponent: () =>
          import('./features/backoffice/pages/reports/payments-report-page').then(
            (m) => m.PaymentsReportPage,
          ),
      },
      {
        path: 'reports/open-tables',
        loadComponent: () =>
          import('./features/backoffice/pages/reports/open-tables-report-page').then(
            (m) => m.OpenTablesReportPage,
          ),
      },
      {
        path: 'vat',
        canActivate: [superGuard],
        loadComponent: () =>
          import('./features/backoffice/pages/vat/vat-page').then((m) => m.VatPage),
      },
      {
        path: 'payment-types',
        canActivate: [superGuard],
        loadComponent: () =>
          import('./features/backoffice/pages/payment-types/payment-types-page').then(
            (m) => m.PaymentTypesPage,
          ),
      },
      {
        path: 'self-pay-types',
        canActivate: [superGuard],
        loadComponent: () =>
          import('./features/backoffice/pages/self-pay-types/self-pay-types-page').then(
            (m) => m.SelfPayTypesPage,
          ),
      },
      {
        path: 'qr-templates',
        canActivate: [superGuard],
        loadComponent: () =>
          import('./features/backoffice/pages/qr-templates/qr-templates-page').then(
            (m) => m.QrTemplatesPage,
          ),
      },
      {
        path: 'roles',
        canActivate: [superGuard],
        loadComponent: () =>
          import('./features/backoffice/pages/roles/roles-page').then((m) => m.RolesPage),
      },
      { path: '', redirectTo: 'users', pathMatch: 'full' },
    ],
  },
  {
    path: 'waiter',
    canActivate: [authGuard],
    loadComponent: () => import('./features/waiter/waiter-page').then((m) => m.WaiterPage),
    children: [
      {
        path: 'tables',
        loadComponent: () =>
          import('./features/waiter/tables/waiter-tables-page').then((m) => m.WaiterTablesPage),
      },
      {
        path: 'tables/:id',
        loadComponent: () =>
          import('./features/waiter/tables/waiter-table-detail-page').then(
            (m) => m.WaiterTableDetailPage,
          ),
      },
      {
        path: 'tables/:id/order',
        loadComponent: () =>
          import('./features/waiter/tables/waiter-order-page').then((m) => m.WaiterOrderPage),
      },
      {
        path: 'approvals',
        loadComponent: () =>
          import('./features/waiter/approvals/waiter-approvals-page').then(
            (m) => m.WaiterApprovalsPage,
          ),
      },
    ],
  },
  { path: '', redirectTo: 'login', pathMatch: 'full' },
  { path: '**', redirectTo: 'login' },
];
