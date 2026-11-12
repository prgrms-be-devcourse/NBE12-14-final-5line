export type FieldError = {
  field: string;
  message: string;
};

export type ApiFailure =
  | {
    kind: 'http';
    message: string;
    status: number;
    code?: string;
    requestId?: string;
    fieldErrors: FieldError[];
  }
  | {
    kind: 'network' | 'timeout' | 'canceled';
    message: string;
  };
