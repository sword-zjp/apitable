import { Injectable, OnApplicationBootstrap } from '@nestjs/common';
import { IField } from '@apitable/core';
import { IFieldValue } from '../../shared/interfaces';
import { BaseUnEditableField } from 'fusion/field/base.un.editable.field';
import { FieldManager } from '../field.manager';

@Injectable()
export class AutoNumberField extends BaseUnEditableField implements OnApplicationBootstrap {
  onApplicationBootstrap(): any {
    FieldManager.setService(AutoNumberField.name, this);
  }

  validate(fieldValue: IFieldValue, field: IField, extra?: { [key: string]: string }) {
    this.throwException(field, 'api_params_automumber_can_not_operate', extra);
  }
}
