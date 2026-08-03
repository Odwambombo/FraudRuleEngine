import './preview.css'

export const parameters = {
  a11y: {
    test: 'error'
  },
  actions: {
    argTypesRegex: '^on[A-Z].*'
  },
  controls: {
    matchers: {
      color: /(background|color)$/i,
      date: /Date$/i
    }
  },
  options: {
    storySort: {
      order: ['Authentication', 'Assessments', 'Dashboard']
    }
  }
}

export const tags = ['autodocs']
