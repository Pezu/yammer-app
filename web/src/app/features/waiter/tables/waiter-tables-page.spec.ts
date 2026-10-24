import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { WaiterTablesPage } from './waiter-tables-page';
import {
  AssignableOrderPoint,
  OrderPointService,
} from '../../backoffice/pages/order-points/order-point.service';
import { OrderPointTypeService } from '../../backoffice/pages/order-point-types/order-point-type.service';
import { AuthService } from '../../../core/auth.service';

const mine: AssignableOrderPoint = {
  id: 'op-1',
  name: 'T12',
  typeId: 'ty-table',
  allowMultipleUsers: false,
  keepOpen: true,
  assignedCount: 1,
  assignedToMe: true,
  assignedNames: ['Ana'],
};

function setup(unassignStatus: number) {
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      { provide: AuthService, useValue: { session: () => ({ locationId: 'loc-1' }) } },
      {
        provide: OrderPointTypeService,
        useValue: { list: () => of([{ id: 'ty-table', type: 'TABLE' }]) },
      },
      {
        provide: OrderPointService,
        useValue: {
          assigned: () => of([]),
          assignable: () => of([mine]),
          unassign: () => throwError(() => ({ status: unassignStatus })),
        },
      },
    ],
  });
  const fixture = TestBed.createComponent(WaiterTablesPage);
  fixture.detectChanges();
  return fixture.componentInstance;
}

describe('WaiterTablesPage — assign picker', () => {
  it('shows a refused unassign inside the picker, not on the page behind it (UX-W1)', () => {
    const page = setup(409);
    page.openPicker();
    page.toggle(page.pickerTiles()[0]);

    expect(page.pickerError()).toBe(page.t('tables.openRefused'));
    expect(page.error()).toBeNull();
    expect(page.pickerOpen()).toBe(true);
  });

  it('reports any other assignment failure in the picker too', () => {
    const page = setup(500);
    page.openPicker();
    page.toggle(page.pickerTiles()[0]);

    expect(page.pickerError()).toBe(page.t('tables.assignFailed'));
    expect(page.error()).toBeNull();
  });
});
