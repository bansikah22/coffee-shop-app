export type Account = {
  id: number
  email: string
  role: 'ADMIN' | 'CUSTOMER'
  authProvider: 'local' | 'keycloak'
}